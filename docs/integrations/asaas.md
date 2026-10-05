# Asaas Sandbox — contrato e garantias (C04, revalidado em 30/09/2026)

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

## Checkout hospedado — referência revisada em 30/09/2026

- A referência atual de criação usa `POST /v3/checkouts`, header
  `access_token`, `billingTypes` com `PIX` e/ou `CREDIT_CARD`, `chargeTypes`
  contendo `DETACHED`, `items`, `callback` e `minutesToExpire` entre 10 e 1440.
  A resposta inclui `id`, `link`, `status` e `externalReference` ([criar checkout](https://docs.asaas.com/reference/create-new-checkout), [guia do Checkout](https://docs.asaas.com/docs/asaas-checkout)).
- A criação apenas abre a jornada hospedada; não confirma pagamento. O callback
  serve para navegação, enquanto o estado financeiro deve vir dos webhooks
  ([guia do Checkout](https://docs.asaas.com/docs/asaas-checkout)).
- A documentação atual de pagamentos oferece filtros `externalReference` e
  `checkoutSession` para localizar cobranças; a recuperação individual usa o
  ID Asaas da cobrança ([listar cobranças](https://docs.asaas.com/reference/list-payments), [consultar cobrança](https://docs.asaas.com/reference/retrieve-a-single-payment)).
- A referência de listagem desaconselha consultas contínuas de estado e recomenda
  webhooks para acompanhar mudanças. Use a listagem por referência/sessão como
  reconciliação pontual e limitada, não como polling recorrente.
- **Lacuna a provar no C04:** a documentação consultada não descreve filtro de
  `/checkouts` por `externalReference`, nem uma chave de idempotência para
  criar checkout. A busca de cobrança por `externalReference` pode ajudar a
  conciliar um pagamento que já tenha sido criado, mas não prova a existência
  nem recupera o link de um checkout ainda não pago. Essa distinção é inferência
  a partir das operações documentadas, não garantia explícita do Asaas. O
  contrato interno atual `findCheckout(paymentIntentId)` pressupõe capacidade
  de recuperar o checkout e precisa ser validado ou substituído antes de
  habilitar retries de criação.

## Payload de webhook de checkout (revalidado em 30/09/2026)

Exemplo oficial em [Eventos para Checkout](https://docs.asaas.com/docs/eventos-para-checkout):
`id`, `event`, `dateCreated` (sem fuso explícito), `account`, `checkout.{id, status, items, customer, …}`.
Não há valor total na raiz: a C60 guarda só `id`/`event`/`checkout.id`/`checkout.status` e a confirmação
consulta o provedor (C61/C64). `customerData` pode trazer dados pessoais e não é persistido.

### Recuperação da resposta perdida de criação (rechecagem em 30/09/2026)

- A referência de criação continua documentando `externalReference` como identificador
  definido pela integração, mas não documenta uma operação de listagem/busca de Checkouts
  por esse campo. A referência documentada é [`POST /v3/checkouts`](https://docs.asaas.com/reference/criar-novo-checkout).
- A documentação de eventos recomenda associar `checkout.id` ao pedido e seu exemplo de
  `CHECKOUT_CREATED` não contém `checkout.externalReference`; a tabela de campos também não
  o lista ([Eventos para Checkout](https://docs.asaas.com/docs/eventos-para-checkout)). Portanto,
  o exemplo publicado não prova que esse campo esteja ausente em eventos reais, mas tampouco
  documenta que o webhook permita recuperar um checkout a partir da referência interna.
- A documentação de boas práticas recomenda verificar se o Checkout anterior foi criado antes
  de recriá-lo após uma falha, sem especificar o mecanismo de consulta ([Erros comuns e boas
  práticas](https://docs.asaas.com/docs/erros-comuns-e-boas-praticas)). Não tratar essa recomendação
  como garantia de busca ou idempotência.
- A confirmação de pagamento no sandbox recebe o ID da cobrança/pagamento em
  `POST /v3/sandbox/payment/{id}/confirm`; esse ID não é `checkout.id`. A operação
  simula confirmação e só se aplica ao sandbox ([confirmar pagamento](https://docs.asaas.com/reference/confirmar-pagamento)).
- No spike C04, enviar uma referência única, interromper/descartar a resposta de criação e provar
  se `CHECKOUT_CREATED` chega com essa referência e se é possível recuperar o `id` e o link sem
  repetir o `POST`. Registrar separadamente o caso em que o webhook não chega. Até essa prova,
  a operação permanece `UNKNOWN` e não pode disparar uma nova criação automaticamente.

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
   criação. A referência atual não documenta busca de checkout por referência;
   testar no sandbox se existe recurso/filtro adicional e como correlacionar
   criação cuja resposta foi perdida. Até lá, preservar `UNKNOWN` sem repetir a
   criação.
2. `minutesToExpire` aceita 10–1440 min segundo o plano; **revalidar o
   intervalo na referência de criação** durante o spike e compatibilizar com
   a reserva de 15 min (se a fila atrasar e não houver tempo de link válido,
   a compra expira de forma controlada).
3. Confirmação no sandbox usa o ID da cobrança/pagamento conhecido, nunca
   presumir que `checkout.id` serve como `{id}`. Confinar a ferramenta local;
   a operação é simulada e nunca exposta ao cliente.
4. Reembolso integral Pix/cartão: verificar suporte por meio, latência e
   estados; resultado incerto permanece em conciliação (C65/C67).

## Pendências do spike (contas do usuário)

- Conta sandbox Asaas + API key; segredo do webhook (32–255 chars, sem
  espaços); ferramenta de túnel escolhida; dados de pagador de teste.
- Sem isso, C04 permanece parcial (docs) e G1/sandbox seguem pendentes.
