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

## Mapeamento para os módulos

- `shipping` (C42): adapter envia **todos** os pacotes (dimensões externas +
  peso total com proteção, D59/D58/D60) do CEP 66053-000 ao destino;
  `insurance_value` = preço do snapshot; parse valida cobertura/custos/prazos
  via `custom_*`; timeout/credencial expirada/serviço ausente tratados.
- Quote persistida e vinculada a itens + endereço + composição (C43);
  qualquer alteração invalida a cotação (V12).
- Etiquetas/tracking por pacote com estado e correlação; falha parcial
  preserva pacotes concluídos (C70/C71, V15/V22) — endpoints de
  compra/geração de etiqueta e tracking a exercitar no spike.

## C70 — contrato de criação e compra revalidado (2026-10-03)

- `POST /api/v2/me/cart` cria uma etiqueta por requisição e retorna o ID usado
  em compra, geração e consultas seguintes. O corpo inclui `service`, remetente,
  destinatário, `products`, `volumes` e opções; `User-Agent` com nome da
  aplicação e e-mail de suporte é obrigatório.
- Embora uma cotação possa retornar vários pacotes, Correios (serviços 1, 2 e
  17), J&T, Loggi e serviço 27 não aceitam volumes múltiplos numa etiqueta.
  Preservar a composição da cotação e criar um ID por pacote quando aplicável;
  não dividir depois do aceite nem assumir que uma unidade local é sempre um
  pacote. A resposta de uma cotação com vários volumes pode exigir várias
  chamadas individuais ao carrinho.
- `POST /api/v2/me/shipment/checkout` e `/generate` aceitam array `orders` de
  IDs. As referências não descrevem resultado parcial/atomicidade por ID; até
  homologar, enviar e persistir cada ID individualmente para correlacionar os
  resultados e não repetir silenciosamente operações já concluídas.
- Os campos fiscais dependem do tipo de envio: a documentação exige chave da
  nota e inscrição estadual do remetente para envios comerciais; DC-e usa
  produtos declarados e, desde 06/04/2026, requer `products` completos no
  carrinho. A documentação do endpoint informa serviço; política/documentos
  fiscais locais e credenciais continuam pendentes antes do sandbox.

Referências oficiais: [inserir frete no carrinho](https://docs.melhorenvio.com.br/reference/inserir-fretes-no-carrinho),
[compra de fretes](https://docs.melhorenvio.com.br/reference/compra-de-fretes-1),
[geração de etiquetas](https://docs.melhorenvio.com.br/reference/geracao-de-etiquetas),
[manual de compra e regra multi volume](https://docs.melhorenvio.com.br/docs/compra-de-fretes).

### Cliente HTTP de escrita C70

- `MelhorEnvioLabelClient` usa exclusivamente a base fixa do sandbox e só é
  registrado quando `SHIPPING_MELHOR_ENVIO_ENABLED=true`. Configure
  `SHIPPING_MELHOR_ENVIO_TOKEN` e
  `SHIPPING_MELHOR_ENVIO_USER_AGENT` (nome do app e e-mail de contato técnico).
  Conexão e leitura têm limites de 3 s e 10 s.
- Carrinho envia o JSON do snapshot de expedição já aceito e exige HTTP 201
  mais `id` textual na resposta. Checkout e geração enviam exatamente um ID
  conhecido em `orders` por requisição e exigem HTTP 200. Os corpos agregados de checkout/geração são
  devolvidos ao chamador sem interpretação por ID; o serviço de aplicação deve
  aguardar evidência C04 antes de avançar/persistir esses resultados.
- HTTP 422 é classificado como rejeição definitiva conforme a resposta de
  validação documentada. Outros status HTTP, timeout/conexão interrompida,
  JSON ilegível, status 2xx diferente do documentado ou resposta do carrinho
  sem ID parseável são `UNKNOWN`. A exceção não contém token, corpo do provedor
  nem dados pessoais. O cliente não faz retry e não persiste/loga a resposta.
- Ainda não existe fluxo administrativo que invoque o cliente: snapshot,
  dados fiscais autorizados, C04 e persistência por etapa/unidade precisam ser
  ligados antes de habilitar uso. Nenhuma credencial está configurada e nenhuma
  chamada real foi feita.

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
