# Spec de pagamentos — C53

## 0. Metadados

- Módulo: `payments`
- Status: proposta de especificação para revisão
- Decisões base: D08–D13, D17, D34, D64; `SPEC-eventing` (aprovada em 2026-09-24); limitações do Asaas em [`docs/integrations/asaas.md`](../docs/integrations/asaas.md)
- Dependências: eventing (outbox/consumo), orders (somente por identificadores e valores recebidos em comandos). `payments` não importa classes de `orders`, `checkout` nem `pricing`.
- Personas: comprador (convidado ou cliente), administrador, sistema (worker, webhook, conciliação)

## 1. Objetivo ◆

Cobrar o total canônico de um pedido por **página hospedada do Asaas Sandbox**
(Pix e cartão, D10), sem confundir retorno de navegação com pagamento, e sem
criar duas cobranças quando a resposta do provedor se perde. Cada efeito
externo (criar checkout, reembolsar) nasce como **intenção durável** antes da
chamada HTTP; resultados incertos ficam `UNKNOWN` até conciliação.

Fora de escopo: dados de cartão (nunca passam pela loja), cobrança parcial,
reembolso parcial (D12), parcelamento, coordenação do pedido/reserva (SPEC-checkout,
C56) e homologação real no sandbox (C04, dependente de conta do usuário).

## 2. Comandos ◆

Alvos futuros (só valem depois de C54/C55 existirem e serem executados):

- `./backend/mvnw -f backend/pom.xml -Dtest='PaymentTransitionsTest' test`
- `./backend/mvnw -f backend/pom.xml -Dit.test='PaymentIntentIT,AsaasCheckoutContractIT' verify`
- `npm --prefix frontend run contracts:check`

## 3. Estrutura ◆

- `B/payments`: `domain` (`PaymentStatus`, `PaymentTransitions`, `ExternalOperationStatus`), `application` (solicitar cobrança, aplicar resultado, solicitar reembolso, conciliar), `adapter/persistence` (JDBC + migration), `adapter/asaas` (cliente HTTP isolado atrás de um port `PaymentProvider`), `adapter/web` (webhook em C60).
- `M/`: `payment_intent` (uma por pedido cobrado), `payment_external_operation` (uma por tentativa externa), `payment_provider_event` (inbox do webhook, C60).
- `contracts/events/`: exemplos dos eventos da §8 validados pelo envelope.
- Não criar: abstração multi-provedor além do port único, endpoint público para mudar estado de pagamento, armazenamento de dados de cartão.

## 4. Estilo e convenções ◆

- Dinheiro em centavos `long`, moeda `BRL` (D17); instantes `Instant` UTC; identificadores UUID.
- Estados em inglês maiúsculo; mensagens ao comprador na interface.
- Erros Problem Details com `codigo` `PAYMENT_NNN` e `correlationId`.
- Diagnóstico de falha externa no formato `KIND:ExceptionClass` ou `HTTP_<status>`, sem corpo, token ou dado pessoal (mesma regra da C49).

## 5. Estratégia de testes ◆

| Critério | Teste | Onde |
|---|---|---|
| PAY-001 intent imutável | `PaymentIntentIT.referenceAndAmountCannotChange` | Testcontainers |
| PAY-002 uma intent por pedido | `PaymentIntentIT.sameOrderReturnsSameIntent` | Testcontainers |
| PAY-003 operação registrada antes do HTTP | `PaymentIntentIT.operationIsDurableBeforeProviderCall` (provedor falso que lê o banco na chamada) | Testcontainers |
| PAY-004 nada de HTTP dentro de transação | `PaymentIntentIT.providerCallRunsWithoutActiveTransaction` | Testcontainers |
| PAY-005 transições | `PaymentTransitionsTest` (tabela origem×destino completa) | unitário |
| PAY-006 timeout depois do efeito → `UNKNOWN` sem nova cobrança | `AsaasCheckoutContractIT.timeoutAfterEffectKeepsUnknown` | WireMock (C55) |
| PAY-007 retorno de navegação não confirma | `AsaasCheckoutContractIT.callbackDoesNotConfirm` | WireMock (C55) |
| PAY-008 evento de provedor repetido/fora de ordem | `PaymentResultIT.duplicateAndStaleEventsDoNotRegress` | Testcontainers |
| PAY-009 valor divergente | `PaymentResultIT.amountMismatchGoesToReview` | Testcontainers |
| PAY-010 reembolso integral único | `RefundIT.secondRefundRequestIsNoOp` | Testcontainers + WireMock |

Relógio controlado; sem `sleep`; resultado do simulador nunca é apresentado como homologação Asaas.

## 6. Limites de atuação ◆

- A unicidade de `externalReference` no Asaas e a existência de chave de idempotência na criação **não estão provadas** (C04). Esta spec assume o pior caso: não há repetição segura; timeout vira `UNKNOWN` e conciliação.
- **PAY-Q01 (aberta):** `minutesToExpire` do link. Proposta: 15 min menos o tempo já decorrido da reserva, com mínimo aceito pelo provedor; se não houver tempo útil, a compra expira de forma controlada sem criar link. Momento: C59, depois de revalidar o intervalo no spike.
- **PAY-Q02 (aberta):** quanto tempo uma operação `UNKNOWN` sem conclusão aguarda antes de ir para análise administrativa. Proposta: 3 consultas de conciliação espaçadas pelo backoff aprovado da C45, depois análise. Momento: C64.
- **PAY-Q03 (aberta):** estado `EXPIRED` do pagamento para `CHECKOUT_EXPIRED`. Proposta: estado próprio terminal, sem efeito no pedido além do que a reserva já determina. Momento: C58.
- Silêncio não é aprovação: as propostas acima só valem após revisão.

## 7. Regras e invariantes

### 7.1 Intent e operação externa

- R01: uma `payment_intent` por pedido (`order_id` único), criada na mesma transação do pedido pendente (C58a) com `amount_cents`, `currency` e `reference` (= `orderId`) imutáveis.
- R02: cada chamada ao provedor é precedida por uma `payment_external_operation` com identidade própria, tipo (`CREATE_CHECKOUT`, `REFUND`, `QUERY`), estado `PENDING` e instante de início, gravada e **commitada** antes do HTTP.
- R03: o worker reclama a operação (lease da C45), commita, chama o provedor fora de transação e grava o resultado em outra transação. Lease vencido não autoriza repetir uma operação `IN_FLIGHT`: ela vira `UNKNOWN`.
- R04: resposta perdida, timeout ou 5xx após envio → operação `UNKNOWN`; nunca se cria outra cobrança para destravar. Só `QUERY`/conciliação decide.
- R05: 4xx de validação antes de efeito → operação `FAILED` e intent `DECLINED` com motivo `PROVIDER_REJECTED`.
- R06: retorno do navegador (`callback`) só leva a tela a consultar o backend; confirmação vem de webhook autenticado e validado por consulta ao provedor (C60/C61).
- R07: valor confirmado diferente de `amount_cents` → intent `UNDER_REVIEW`, sem confirmar o pedido.
- R08: reembolso é sempre integral (D12), no máximo um por intent (constraint), e só a partir de `CONFIRMED` ou `UNDER_REVIEW` com pagamento recebido.

### 7.2 Estados da intent

`REQUESTED`, `CREATING_CHECKOUT`, `AWAITING_PAYMENT`, `CONFIRMED`, `DECLINED`, `UNKNOWN`, `UNDER_REVIEW`, `REFUND_REQUESTED`, `REFUNDED`.

Terminais: `DECLINED`, `REFUNDED` (e `EXPIRED`, se PAY-Q03 for aprovada).

| Origem | Destino | Ator | Precondição | Operação atômica | Evento |
|---|---|---|---|---|---|
| — | `REQUESTED` | checkout | pedido pendente criado | intent + outbox | `payment.checkout_requested` |
| `REQUESTED` | `CREATING_CHECKOUT` | worker | operação `CREATE_CHECKOUT` reclamada | intent + operação `IN_FLIGHT` | — |
| `CREATING_CHECKOUT` | `AWAITING_PAYMENT` | worker | provedor devolveu checkout e link | intent + operação `SUCCEEDED` + outbox | `payment.checkout_available` |
| `CREATING_CHECKOUT` | `DECLINED` | worker | 4xx antes de efeito | intent + operação `FAILED` + outbox | `payment.status_changed` |
| `CREATING_CHECKOUT` | `UNKNOWN` | worker | timeout/5xx/lease vencido | intent + operação `UNKNOWN` | `payment.status_changed` |
| `UNKNOWN` | `AWAITING_PAYMENT`/`CONFIRMED`/`DECLINED` | conciliação | consulta conclusiva no provedor | intent + operação `QUERY` + outbox | `payment.status_changed` |
| `UNKNOWN` | `UNDER_REVIEW` | conciliação | sem conclusão após PAY-Q02 | intent + outbox | `payment.status_changed` |
| `AWAITING_PAYMENT` | `CONFIRMED` | webhook validado | valor igual e evento mais novo | intent + inbox processada + outbox | `payment.status_changed` |
| `AWAITING_PAYMENT` | `UNDER_REVIEW` | webhook validado | valor divergente | intent + outbox | `payment.status_changed` |
| `CONFIRMED`/`UNDER_REVIEW` | `REFUND_REQUESTED` | checkout/admin | compensação (D13) ou cancelamento pago (D12, D29, D31) | intent + operação `REFUND` + outbox | `payment.refund_requested` |
| `REFUND_REQUESTED` | `REFUNDED` | worker/webhook | reembolso confirmado pelo provedor | intent + outbox | `payment.refunded` |
| `UNDER_REVIEW` | `CONFIRMED` | admin | decisão registrada com motivo | intent + outbox | `payment.status_changed` |

Qualquer par ausente é rejeitado (`PAYMENT_002`, 409). Evento de provedor mais antigo que o último aplicado é registrado e ignorado.

### 7.3 Pagamento tardio

Pagamento confirmado depois da reserva expirar não confirma o pedido nem consome estoque: o fato `payment.status_changed` para `CONFIRMED` é aplicado pelo checkout, que coloca o pedido `UNDER_REVIEW` e solicita reembolso (D13). A decisão é do checkout (C56); pagamentos só registra o fato.

## 8. Contratos

- HTTP: nenhuma rota pública nesta fatia. O estado do pagamento aparece no pedido/checkout (C61). Webhook `POST /api/v1/webhooks/asaas` fica em C60.
- Erros reservados: `PAYMENT_001` intent inexistente (404), `PAYMENT_002` transição inválida (409), `PAYMENT_003` valor divergente (registrado, sem resposta HTTP).

### Eventos emitidos

| Tipo | schemaVersion | `aggregateId` | Payload mínimo (sem dados pessoais) |
|---|---|---|---|
| `payment.checkout_requested` | 1 | `paymentIntentId` | `paymentIntentId`, `orderId`, `amountCents`, `currency` |
| `payment.checkout_available` | 1 | `paymentIntentId` | `paymentIntentId`, `orderId`, `expiresAt` |
| `payment.status_changed` | 1 | `paymentIntentId` | `paymentIntentId`, `orderId`, `from`, `to`, `reason` |
| `payment.refund_requested` | 1 | `paymentIntentId` | `paymentIntentId`, `orderId`, `amountCents` |
| `payment.refunded` | 1 | `paymentIntentId` | `paymentIntentId`, `orderId`, `amountCents` |

O link hospedado não vai em evento: é lido pelo checkout no banco de pagamentos via contrato público do módulo. Exemplos em `contracts/events/examples/payment-*.json`.

## 9. Critérios de aceitação documental

| ID | Critério | Evidência |
|---|---|---|
| PAY-DOC-1 | Pix/cartão por página hospedada; callback não confirma | §1, R06 |
| PAY-DOC-2 | Correlação pedido↔intent↔checkout e valor imutável | R01, R07 |
| PAY-DOC-3 | Resultado desconhecido, consulta e reembolso definidos | R03–R05, R08, §7.2 |
| PAY-DOC-4 | Contrato não depende de classes de pedido | §0, §3 |
| PAY-DOC-5 | Limitações do Asaas incorporadas sem alegar homologação | §6 |
