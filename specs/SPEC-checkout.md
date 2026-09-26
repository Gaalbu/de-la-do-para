# SPEC — Endereço e escolha de entrega no checkout

Status: contrato executável para C44; não autoriza cobrança, reserva de estoque
ou criação de pedido.

## Escopo

O checkout lê um snapshot imutável do carrinho e coleta somente os dados
necessários para obter opções de entrega. O cliente nunca calcula frete, prazo
ou total: esses valores vêm do servidor e permanecem ligados ao snapshot.

## Endereço

Para entrega, o cliente envia `postalCode` como oito dígitos, opcionalmente
formatado com hífen, e os campos complementares exigidos pelo serviço de
destino. O servidor normaliza o CEP antes de cotar e rejeita CEP inválido,
ausente ou incompatível com a modalidade. A interface não preenche rua,
número, bairro ou complemento por inferência.

Retirada não exige endereço de entrega e usa somente um ponto de retirada
retornado pelo servidor. O ponto sintético de demonstração deve ser claramente
identificado e não pode ser apresentado como atendimento comercial.

## Cotação

`GET /api/v1/checkout/{snapshotId}/delivery-options` recebe a versão do
snapshot e o endereço normalizado. A resposta contém `snapshotVersion`, um
`inputFingerprint`, estado (`AVAILABLE` ou `UNAVAILABLE`) e, quando disponível,
as opções:

```json
{
  "snapshotVersion": 3,
  "inputFingerprint": "sha256:...",
  "options": [
    {
      "id": "quote-uuid",
      "mode": "DELIVERY",
      "label": "Sandbox PAC",
      "priceCents": 2590,
      "preparationDays": 2,
      "deliveryDays": 5,
      "expiresAt": "2026-09-22T13:00:00Z"
    }
  ]
}
```

`UNAVAILABLE` é uma resposta recuperável: a UI mantém o carrinho e oferece
alterar o endereço, escolher retirada quando compatível ou tentar nova cotação.
Não exibe custo ou prazo estimado sem uma opção persistida e válida.

## Invalidação

Alterar qualquer item do carrinho, sua versão, o endereço ou a modalidade
descarta a seleção local e exige nova cotação. Aceitar uma opção envia seu
`quoteId`, `snapshotVersion` e `inputFingerprint`; o servidor revalida os três,
a disponibilidade e `expiresAt`, retornando `409` para identidade divergente e
`410` para cotação expirada.

## Retirada

Uma opção de retirada contém ponto, janela e prazo de preparo retornados pelo
servidor. A UI não cria horários, não remove itens incompatíveis silenciosamente
e mantém a escolha separada das opções de entrega.

## Erros

- `400`: endereço ou modalidade inválida;
- `404`: snapshot inexistente ou não pertencente ao visitante;
- `409`: snapshot, fingerprint ou seleção obsoleta;
- `410`: cotação expirada;
- `422`: composição sem modalidade compatível.

Todos os problemas usam `codigo` e `correlationId`; não expõem credenciais de
adapter nem o identificador bruto da sessão.

---

# Aceite da compra e compensação — C56

Status: proposta de especificação para revisão. Base: D11–D13, D29–D34, D37,
D64, D66; `SPEC-inventory`, `SPEC-pricing` (C31), `SPEC-orders`,
`SPEC-payments`, `SPEC-eventing`. As perguntas CHK-Q01…Q04 não bloqueiam as
partes independentes delas.

## A1. Objetivo

Transformar o snapshot do checkout em uma compra aceita **em uma única
transação PostgreSQL**: pedido pendente, reserva de estoque por 15 minutos,
reserva de uso de cupom, intenção de pagamento e eventos na outbox. Depois do
aceite, pagamento confirmado, expiração, cancelamento e reembolso movem
pedido, reserva, cupom e pagamento juntos, sem resultado contraditório.

Fora de escopo: cobrança no provedor (SPEC-payments), expedição/etiquetas
(C70+), notificações (C74+).

## A2. Idempotência do aceite (C58)

- `POST /api/v1/checkout/{snapshotId}/purchase` exige `Idempotency-Key`
  (16–160 caracteres visíveis). O registro é único por
  **sujeito + operação + chave**: sujeito = `accountId` da sessão ou a chave da
  sessão do convidado; operação = `checkout.purchase`.
- Hash canônico SHA-256 da intenção: `snapshotId`, versão do carrinho,
  seleção de entrega/retirada, e-mail normalizado, código de cupom
  normalizado e `summaryVersion` (A3). Ordem de campos fixa, sem espaços.
- Estados: `PENDING` (aceite em curso) e `COMPLETED` (com `orderId`).
  - Mesma chave + mesmo hash + `COMPLETED` → `200` com o mesmo pedido (V02).
  - Mesma chave + mesmo hash + `PENDING` → `409 CHECKOUT_010` com
    `Retry-After: 1`.
  - Mesma chave + hash diferente → `422 CHECKOUT_011` (V03).
  - Mesma chave de outro sujeito → registro independente (V03).
- O claim e o aceite estão na **mesma transação**: rollback apaga o claim, e
  a nova tentativa recalcula tudo. Rejeições de negócio (sem estoque, cupom
  esgotado, resumo alterado) não gravam resultado.
- **CHK-Q01 (aberta):** retenção do registro. Proposta: 30 dias, igual à
  outbox publicada aprovada na C45. Momento: antes de qualquer limpeza.

## A3. Resumo versionado

- O resumo mostrado ao comprador (linhas, preços unitários, frete, prazo,
  desconto e total) tem `summaryVersion` = hash canônico desses valores,
  calculado pelo servidor.
- No aceite o servidor recalcula o resumo com preço atual (C30), cotação
  ainda válida (C42) e cupom (C31). Diferença → `409 CHECKOUT_012` com o
  resumo novo; nada é gravado (V12). O comprador confirma de novo.
- Itens adicionados ao carrinho depois do snapshot permanecem no carrinho
  (consumo apenas das linhas e quantidades compradas).

## A4. Transação de aceite (C58a)

Ordem dentro da transação, que falha inteira em qualquer passo:

1. Claim de idempotência (A2).
2. Verificar dono do snapshot e versão do carrinho; recalcular resumo (A3).
3. Reservar estoque por SKU em ordem estável de `skuId`, lotes elegíveis
   para a chegada prevista (C57, V01, V13). Reserva: 15 min (D11).
4. Reservar uso do cupom (C31, V11).
5. Criar pedido `PENDING_PAYMENT` com snapshot por valor (C51); emitir token
   de convidado quando não houver conta (C52).
6. Criar intenção de pagamento e operação `CREATE_CHECKOUT` (C54).
7. Consumir do carrinho as quantidades compradas.
8. Marcar a chave `COMPLETED` com `orderId`.

Nenhuma chamada HTTP e nenhum envio a Kafka acontecem na transação. Kafka fora
não afeta a resposta: os eventos esperam na outbox (V04).

Resposta `201`: `orderId`, estado, total, `expiresAt` da reserva e, para
convidado, o token de acesso mostrado uma única vez.

- **Implementação C58a:** cupom exige conta com e-mail verificado; convidado recebe `CHECKOUT_014` com motivo `EMAIL_NOT_VERIFIED` até existir verificação de e-mail de convidado (D33). Total zero é recusado com `422 CHECKOUT_016`.
- **CHK-Q02 (aberta):** replay de aceite de convidado não pode repetir o token
  bruto (só o hash é guardado). Proposta: o replay devolve o pedido sem token,
  com `accessTokenIssued: true`, e o link por e-mail (C75) é o caminho de
  recuperação. Momento: C58a.

## A5. Estados combinados

| Pedido | Reserva | Cupom | Pagamento | Significado |
|---|---|---|---|---|
| `PENDING_PAYMENT` | `ACTIVE` | `RESERVED` | `REQUESTED`…`AWAITING_PAYMENT`/`UNKNOWN` | aguardando pagamento |
| `PAID` | `COMMITTED` | `CONSUMED` | `CONFIRMED` | compra efetivada |
| `EXPIRED` | `RELEASED` | `RELEASED` | qualquer não confirmado | prazo esgotado sem pagamento |
| `UNDER_REVIEW` | `RELEASED` | `RELEASED` | `UNDER_REVIEW`→`REFUND_REQUESTED` | pagamento tardio (D13) |
| `CANCELLED` | `RELEASED` ou devolvida | `RELEASED` ou `CONSUMED` até reembolso | `REFUND_REQUESTED`/`REFUNDED` se pago | cancelada |

## A6. Transições coordenadas

Cada linha é uma transação local; o pedido é travado (`FOR UPDATE`) antes de
qualquer outra linha, sempre na ordem pedido → reserva → cupom → pagamento.

| Gatilho | Precondição (lida sob lock) | Efeitos atômicos | Eventos |
|---|---|---|---|
| Pagamento `CONFIRMED` (C61) | pedido `PENDING_PAYMENT`, reserva `ACTIVE` e `now < expiresAt`, valor igual | reserva `COMMITTED`, cupom `CONSUMED`, pedido `PAID` | `order.status_changed` |
| Pagamento `CONFIRMED` tardio (D13, V10) | reserva vencida ou liberada | pedido `PENDING_PAYMENT`→`UNDER_REVIEW` (`LATE_PAYMENT`) se ainda pendente; reserva e cupom liberados; pagamento `UNDER_REVIEW`→`REFUND_REQUESTED` | `order.status_changed`, `payment.refund_requested` |
| Pagamento `CONFIRMED` com valor divergente | qualquer | pedido inalterado; pagamento `UNDER_REVIEW` (SPEC-payments R07) | `payment.status_changed` |
| Expiração (job, relógio controlado) | pedido `PENDING_PAYMENT`, reserva `ACTIVE`, `now >= expiresAt`, pagamento não confirmado | reserva e cupom liberados, pedido `EXPIRED` | `order.status_changed` |
| Cancelamento sem pagamento | pedido `PENDING_PAYMENT` | reserva e cupom liberados, pedido `CANCELLED` | `order.status_changed` |
| Cancelamento pago (D12, D29, D31) | pedido `PAID`/`PREPARING`/`READY_FOR_PICKUP`, nenhum pacote com a transportadora, retirada não confirmada | pedido `CANCELLED`; estoque devolvido (CHK-Q03); cupom segue `CONSUMED`; pagamento `REFUND_REQUESTED` | `order.status_changed`, `payment.refund_requested` |
| Reembolso confirmado | pagamento `REFUND_REQUESTED` | pagamento `REFUNDED`; uso por e-mail do cupom devolvido (D34), limite global permanece gasto (proposta C31) | `payment.refunded` |
| Pedido com pacote na transportadora (D30) | pedido `IN_TRANSIT` | pedido `UNDER_REVIEW`; sem reembolso automático | `order.status_changed` |

O pedido `EXPIRED` é terminal: se o pagamento chegar depois da expiração já
aplicada, o pedido fica `EXPIRED` e só o pagamento segue para análise e
reembolso.

- **CHK-Q03 (aberta):** estoque de pedido pago e cancelado antes do despacho.
  Proposta: movimento de devolução ao mesmo lote, com motivo
  `ORDER_CANCELLED`, sem reabrir lote bloqueado ou vencido. Momento: C66.
- **CHK-Q04 (aberta):** pedido `UNDER_REVIEW` por pagamento tardio, depois do
  reembolso confirmado. Proposta: permanece em análise até o administrador
  cancelar com motivo `REFUND_COMPENSATION` (a tabela da SPEC-orders só
  permite ADMIN nessa saída). Momento: C61.

## A7. Corridas enumeradas antes do coordenador

| ID | Corrida | Mecanismo | Teste previsto |
|---|---|---|---|
| V01 | Duas compras para a última unidade | reserva em ordem estável com lock/versão do lote | `StockReservationIT.lastUnitGoesToOneBuyer` |
| V02 | Mesmo aceite repetido | idempotência `COMPLETED` | `CheckoutIdempotencyIT.sameKeySameIntentReturnsSameOrder` |
| V03 | Mesma chave com outro corpo/sujeito | hash + sujeito | `CheckoutIdempotencyIT.changedBodyConflictsAndSubjectsAreIsolated` |
| V04 | Kafka fora no aceite | outbox na transação | `CheckoutAcceptanceIT.brokerDownStillAccepts` |
| V05 | Queda após envio e antes de marcar outbox | consumo idempotente (C48) | `PaymentOutcomeIT.duplicateEventAppliesOnce` |
| V06 | Queda entre efeito e offset | ledger de consumo transacional | `PaymentOutcomeIT.redeliveryDoesNotMoveStockAgain` |
| V07 | Timeout após criação no provedor | `UNKNOWN` + `findCheckout` (C54/C55) | `CheckoutOperationRunnerIT`, conciliação C64 |
| V08 | Webhook duplicado/antigo | inbox + versão | `AsaasWebhookIT`, `PaymentOutcomeIT.staleEventDoesNotRegress` |
| V09 | Webhook forjado ou valor divergente | token + consulta + comparação de valor | `AsaasWebhookIT.forgedIsRejected`, `PaymentOutcomeIT.amountMismatchGoesToReview` |
| V10 | Reserva expira com confirmação em trânsito | lock do pedido e checagem de `expiresAt` sob lock | `PaymentOutcomeIT.latePaymentGoesToReviewAndRefund` |
| V11 | Cupom em duas compras | lock da linha do cupom (C31) | `CouponReservationServiceIT` (existente) + `CheckoutAcceptanceIT` |
| V12 | Preço/cotação/endereço muda | `summaryVersion` | `CheckoutAcceptanceIT.changedSummaryIsRejectedWithoutWrites` |
| V16 | Cancelamento contra expedição/retirada | lock do pedido; transição validada no estado atual | `OrderCancellationIT.cancelAndPickupAreMutuallyExclusive` |

## A8. Erros

| Código | HTTP | Situação |
|---|---|---|
| `CHECKOUT_010` | 409 | aceite com a mesma chave ainda em curso |
| `CHECKOUT_011` | 422 | chave reutilizada com outra intenção |
| `CHECKOUT_012` | 409 | resumo mudou; corpo traz o resumo novo |
| `CHECKOUT_013` | 409 | estoque insuficiente para algum item |
| `CHECKOUT_014` | 409 | cupom indisponível (limite, validade, mínimo) |
| `CHECKOUT_015` | 400 | `Idempotency-Key` ausente ou malformada |
| `CHECKOUT_016` | 422 | total zero (cupom cobre tudo em retirada) não é suportado |

## A9. Critérios de aceitação documental

| ID | Critério | Evidência |
|---|---|---|
| CHK-DOC-1 | Chave + hash + sujeito e resumo versionado | A2, A3 |
| CHK-DOC-2 | Reserva de 15 min e expiração coordenada | A4, A6 |
| CHK-DOC-3 | Cancelamento, reembolso e pagamento tardio ligados a D12/D13/D29–D31/D34 | A6 |
| CHK-DOC-4 | Corridas V01–V12/V16 enumeradas com mecanismo e teste | A7 |
