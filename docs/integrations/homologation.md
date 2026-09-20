# Roteiro de homologação sandbox (opt-in) — C04/C89

Execução **manual e opt-in**, somente com credenciais do usuário; nunca em
CI pública, nunca com carga, nunca com segredos no Git. IDs reais
sanitizados no relatório. Status: **pendente** (aguarda contas).

## Pré-requisitos (usuário)

- [ ] Conta Asaas sandbox + API key + segredo de webhook (32–255 chars)
- [ ] Conta Melhor Envio sandbox + app OAuth2 + e-mail p/ `User-Agent`
- [ ] Túnel **aprovado pelo usuário (20/09/2026)**: Cloudflare quick tunnel
  (sem cadastro) — expor **só** `/api/v1/webhooks/*`; fallback ngrok
  (exige conta) se o quick tunnel falhar
- [ ] Dados de pagador/remetente de teste aceitos pelos sandboxes

## Passo a passo Asaas

1. Criar checkout Pix (DETACHED) do total do snapshot → guardar `id`.
2. Criar checkout cartão → guardar `id`.
3. Confirmar pagamento de teste (operação de sandbox, ferramenta local).
4. Observar webhooks `CHECKOUT_CREATED` → `CHECKOUT_PAID` via túnel; conferir
   persistência antes do 2xx e dedup em reenvio.
5. Consultar estado por referência; correlacionar checkout↔pagamento.
6. Reembolsar integral e observar estados; registrar latência por meio.
7. Timeout simulado após efeito → UNKNOWN → conciliação sem recriar cobrança.

## Passo a passo Melhor Envio

1. Cotar snapshot multi-pacote (CEP 66053-000 → destino de teste) nos modos
   produto e volume; conferir `custom_price`/`custom_delivery_time` e
   `packages[]` contra nossa composição.
2. Gerar/comprar etiqueta de teste; gerar tracking; acompanhar
   postado (15 min) → entregue (+15 min).
3. Forçar falha parcial (um pacote) e retomar só o pendente.

## Saída

Relatório em `docs/evidence/sandbox-*/` com IDs sanitizados, estados no
provedor × banco × API × browser, latências e limitações. Falha ou falta de
credencial bloqueia só o gate externo; release não se declara homologada
sem este roteiro verde.
