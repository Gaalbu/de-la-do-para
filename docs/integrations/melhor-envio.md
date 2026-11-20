# Melhor Envio Sandbox — contrato e garantias (C04, revalidado em 20/09/2026)

Status: **não homologado** — sem conta/credenciais; nenhuma cotação real foi
executada. Fatos revalidados na documentação oficial; spike precisa provar o
restante. Não alegar integração comprovada.

## Fatos revalidados (docs.melhorenvio.com.br)

- Sandbox: base <https://sandbox.melhorenvio.com.br>, contas e dados
  **separados** da produção; cadastro simplificado com **R$10.000** de saldo
  de teste; só **Correios e JadLog**; pagamentos Yapay/carteira com aprovação
  automática em ~5 min; envios viram *postado* após 15 min e *entregue* após
  +15 min, com repesagem aleatória (créditos/débitos)
  ([intro](https://docs.melhorenvio.com.br/reference/introducao-api-melhor-envio),
  atualizado em 10/11/2025).
- Auth OAuth2: access token 30 dias, refresh 45 dias. Header `User-Agent`
  com nome do app + e-mail de contato é **obrigatório**; `Accept` e
  `Content-Type` JSON; Bearer token
  ([cálculo](https://docs.melhorenvio.com.br/reference/calculo-de-fretes-por-produtos),
  atualizado em 18/06/2026).
- Cotação `POST /api/v2/me/shipment/calculate` em dois modos: **por produtos**
  (dimensões cm, peso kg, `insurance_value`, quantidade por item) ou **por
  pacotes/volumes** (dimensões, peso, `insurance` por volume). Usar
  **`custom_price` e `custom_delivery_time`** (refletem taxas/descontos da
  conta), não `price`/`delivery_time`.
- Resposta traz `packages[]` com preço, dimensões, peso e produtos por
  pacote — base para verificar nossa composição multi-pacote (C41a/V21).
  Erro de validação: 422 com `errors` por campo.

## Implementação local do adapter de cotação (2026-11-03)

- `MelhorEnvioSandboxAdapter` envia POST para o host fixo do sandbox, com
  `Authorization: Bearer`, `User-Agent` configurável, origem/destino e
  volumes em cm/kg com valor segurado por pacote em BRL.
- O parser lê uma lista de serviços, usa `custom_price` e
  `custom_delivery_time`, e rejeita opções cuja resposta não corresponda aos
  pacotes/dimensões/peso/seguro enviados. Com `SHIPPING_ADAPTER_ENABLED=true`,
  o endpoint de opções de entrega do checkout recompõe o snapshot e invoca o
  adapter quando não existe cotação válida com fingerprint atual; por padrão,
  a chamada permanece desabilitada.
- `SHIPPING_ADAPTER_ENABLED=true` habilita o bean. Configurar
  `shipping.adapter.credential` (token), `shipping.adapter.user-agent` (nome
  do app e e-mail de contato), `shipping.adapter.timeout`,
  `shipping.adapter.quote-validity` (TTL local; default 5 minutos) e opcionalmente
  `shipping.adapter.origin-postal-code` (default aprovado `66053000`). A URL
  base pode ser configurada, mas o bean rejeita qualquer host que não seja
  `sandbox.melhorenvio.com.br`.
- Teste usa `MockRestServiceServer`; não é homologação sandbox, e o adapter
  ainda não foi exercitado com credenciais reais.

## Mapeamento para os módulos

- `shipping` (C42): adapter envia **todos** os pacotes (dimensões externas +
  peso total com proteção, D59/D58/D60) do CEP 66053-000 ao destino;
  `insurance_value` = preço do snapshot; parse valida cobertura/custos/prazos
  via `custom_*`; timeout/credencial expirada/serviço ausente tratados.
- Quote persistida e vinculada a snapshot, versão, endereço, composição,
  origem e preços atuais (C43); a seleção recompõe esses dados no servidor e
  rejeita fingerprint obsoleto. Alteração de preço/embalagem invalida a seleção.
- Etiquetas/tracking por pacote com estado e correlação; falha parcial
  preserva pacotes concluídos (C70/C71, V15/V22) — endpoints de
  compra/geração de etiqueta e tracking a exercitar no spike.

## A provar no spike

1. Aceitação do CEP 66053-000 e cobertura PAC/SEDEX/JadLog para rotas de
   teste com nossas caixas P/M/G (externas na cotação).
2. Como o sandbox representa múltiplos volumes, etiquetas e rastreios
   (1 etiqueta por pacote? rastreio por pacote?); latências reais de
   postado/entregue para o roteiro.
3. Renovação de OAuth2 (30/45 dias), endereço inválido, ausência de serviço,
   falha parcial entre operações, resultado desconhecido.
4. Etiqueta de sandbox identificada como teste em toda UI/admin (nunca válida
   para postagem real).

## Pendências do spike (contas do usuário)

- Conta sandbox Melhor Envio + app OAuth2 (client id/secret + redirect);
  e-mail de contato para o `User-Agent`; CEPs de destino de teste.


## Estado de implementação e evidência (2026-11-03)

A branch `codex/c42-live-quotes` conecta o endpoint de opções de entrega do
checkout ao gerador de cotações. O gerador carrega SKU ativo e preço atual,
compõe os volumes, calcula fingerprint com snapshot/versão, origem/destino,
embalagem e seguro, reutiliza opção válida compatível ou chama o adapter e
persiste as respostas. Na seleção, o fingerprint é recalculado no servidor;
preço ou embalagem alterados causam conflito.

Os testes focados locais passaram: 21/21 em `PackageComposerTest`,
`ShippingQuoteGenerationServiceTest`, `ShippingQuoteServiceTest`,
`CheckoutSnapshotServiceTest`, `CheckoutControllerTest` e
`MelhorEnvioSandboxAdapterTest`, com `MockRestServiceServer`. Isso valida o
cliente HTTP contra resposta controlada, não uma chamada real ao carrier. A
integração permanece opt-in (`SHIPPING_ADAPTER_ENABLED=false` por padrão),
sem credenciais reais; CI da branch, aceitação de CEP/rota pelo sandbox e
jornada integrada no navegador ainda precisam de evidência.
