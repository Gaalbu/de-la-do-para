# Asaas Sandbox — contrato e garantias (C04, revalidado em 20/09/2026)

Status: **não homologado** — sem contas/credenciais; nenhuma operação real
foi executada. Este arquivo registra fatos revalidados na documentação
oficial e o que o spike precisa provar. Não alegar integração comprovada.

## Fatos revalidados (docs.asaas.com)

- Checkout hospedado com Pix e/ou cartão: `billingTypes` = `PIX`,
  `CREDIT_CARD`; cobrança avulsa `chargeTypes` = `DETACHED`
  ([Checkout](https://docs.asaas.com/docs/checkout-asaas), atualizado em
  26/08/2026). Campos relevantes: `customer`/`customerData`, `callback`
  (navegação), `minutesToExpire` (validade do link).
- **Criação do checkout não confirma pagamento**; ciclo de vida por webhook:
  `CHECKOUT_CREATED`, `CHECKOUT_PAID`, `CHECKOUT_CANCELED`,
  `CHECKOUT_EXPIRED`. Callbacks controlam navegação e **não** substituem a
  confirmação assíncrona — mesma regra da nossa spec (C53/C60).
- Webhooks: entrega **at-least-once**, retry em não-2xx, fila pausa após 15
  falhas consecutivas, perda permanente após 14 dias; fluxo oficial =
  receber → persistir → responder 200 → processar depois — idêntico ao nosso
  desenho (C60). Idempotência pelo `id` do evento
  ([webhooks](https://docs.asaas.com/docs/receive-asaas-events-at-your-webhook-endpoint),
  atualizado em 22/06/2026).
- Autenticação do webhook: header `asaas-access-token`, token próprio de
  32–255 caracteres, sem espaços; recomendado restringir aos IPs oficiais.
- Sandbox em <https://sandbox.asaas.com/>; túnel sugerido pelo próprio Asaas:
  ngrok ou Cloudflare Tunnel. Debug via página de Webhook Logs.

## Payload de webhook de checkout (revalidado em 25/09/2026)

Exemplo oficial em [Eventos para Checkout](https://docs.asaas.com/docs/eventos-para-checkout):
`id`, `event`, `dateCreated` (sem fuso explícito), `account`, `checkout.{id, status, items, customer, …}`.
Não há valor total na raiz: a C60 guarda só `id`/`event`/`checkout.id`/`checkout.status` e a confirmação
consulta o provedor (C61/C64). `customerData` pode trazer dados pessoais e não é persistido.

## Mapeamento para os módulos

- `payments`: adapter cria checkout a partir do snapshot (total = itens +
  frete − desconto); persiste intent + operação externa antes da chamada;
  nunca chama HTTP dentro de transação (C54/C59).
- `POST /api/v1/webhooks/asaas`: valida token, limite de corpo, persiste
  evento antes do 2xx, deduplica por identidade (C60).
- Reconciliação consulta o estado no provedor e correlaciona
  checkout↔pagamento; fora de ordem não regride estado (C64).

## Adapter de checkout hospedado (C63, revalidado em 03/10/2026)

`AsaasPaymentProvider` (`payments/adapter/asaas`) só é carregado com
`PAYMENTS_PROVIDER=asaas`. Testes: `AsaasPaymentProviderTest`, com respostas
HTTP gravadas a partir da referência oficial; **não** são evidência de sandbox.

- Criação: `POST /v3/checkouts` com header `access_token`, `billingTypes`
  `PIX`+`CREDIT_CARD`, `chargeTypes` `DETACHED`, `externalReference` = id da
  intent e um único item com o total canônico do pedido (itens + frete −
  desconto) em reais. O item exige `imageBase64`; usamos um quadrado
  terracota de 8×8 px. `callback` leva a `/orders/{id}` da loja e só navega.
  [Referência](https://docs.asaas.com/reference/criar-novo-checkout).
- Resposta: `id`, `link`, `status`; não há data de expiração, então
  `expiresAt` = instante do pedido + `minutesToExpire`. O link só é aceito com
  HTTPS e host em `ASAAS_CHECKOUT_HOSTS` (padrão `sandbox.asaas.com`, a
  confirmar no spike); fora disso a operação fica `UNKNOWN`.
- Erro 4xx (exceto 408/409) na criação = recusa antes de efeito. 5xx, 408,
  409, timeout ou corpo ilegível = resultado desconhecido, sem nova tentativa.
- Consulta: a API não documenta GET de checkout. O adapter lê
  `GET /v3/payments?externalReference=<intent>`; `CONFIRMED`, `RECEIVED` e
  `RECEIVED_IN_CASH` contam como pago, com o `value` da cobrança e seu
  `checkoutSession`. A cobrança herdar o `externalReference` do checkout
  **não está documentada**: o spike precisa provar. Lista vazia não prova que
  o checkout não existe, então não serve para repetir uma criação perdida.
  [Listar cobranças](https://docs.asaas.com/reference/listar-cobrancas).
- `minutesToExpire` configurável entre 10 e 15 (padrão 10). O link nunca
  dura mais que a reserva de estoque: a intent nasce na mesma transação da
  reserva de 15 min (D11), então o adapter encurta o link para os minutos
  inteiros que restam até esse prazo, descontado o timeout. Se sobrar menos
  que o mínimo de 10 min da Asaas, recusa sem chamar a API
  (`RESERVATION_TOO_SHORT`, intent `DECLINED`); a reserva nunca é prolongada.
  A regra final depende de PAY-Q01.

## Idempotência e UNKNOWN (a provar no spike)

1. `externalReference` **não** prova unicidade nem chave de idempotência na
   criação — verificar: consulta por referência, correlação
   checkout/pagamento, comportamento após timeout (questão central do C04).
2. `minutesToExpire` aceita 10–1440 min segundo o plano; **revalidar o
   intervalo na referência de criação** durante o spike e compatibilizar com
   a reserva de 15 min (se a fila atrasar e não houver tempo de link válido,
   a compra expira de forma controlada).
3. Operação de confirmação de pagamento em sandbox (sem dinheiro real):
   localizar endpoint atual e confinar ao roteiro/ferramenta local — nunca
   exposto ao cliente.
4. Reembolso integral Pix/cartão: verificar suporte por meio, latência e
   estados; resultado incerto permanece em conciliação (C65/C67).

## Pendências do spike (contas do usuário)

- Conta sandbox Asaas + API key; segredo do webhook (32–255 chars, sem
  espaços); ferramenta de túnel escolhida; dados de pagador de teste.
- Sem isso, C04 permanece parcial (docs) e G1/sandbox seguem pendentes.
