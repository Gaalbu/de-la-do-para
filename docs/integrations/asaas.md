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
