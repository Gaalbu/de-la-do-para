# Spec de preço e cupons — C24a (base documental para C29)

## 0. Metadados

- Módulo: `pricing`
- Status: proposta consolidada para revisão do usuário; fórmulas detalhadas e implementação ficam em C29
- Decisões base: D17, D33–D34, D54
- Dependências: catálogo (`catalog`); pedidos/checkout consomem snapshots calculados

## 1. Objetivo

Definir o que já está decidido sobre moeda, valores fictícios do catálogo e
uso de cupom por convidado, preservando para C29 os cálculos, limites e
concorrência que ainda não foram especificados.

## 2. Regras confirmadas

- Moeda comercial única: BRL; venda por unidade/SKU (D17). Implementação
  deverá representar dinheiro em centavos inteiros, nunca ponto flutuante,
  conforme convenção técnica do plano.
- Preços e unidades fictícios do catálogo seguem D54 e
  `docs/decisions.md`; não são preços de produtores reais.
- Cupom pode ser usado por convidado, vinculado a e-mail verificado, sem
  exigir criação de conta. Cada cupom possui limite configurável de usos por
  e-mail verificado (D33). Isso não garante unicidade por pessoa.
- Reembolso solicitado ou pendente não restaura uso. Somente confirmação de
  reembolso integral devolve a elegibilidade daquele e-mail, preservando
  histórico e respeitando validade e limite global (D34).

### Exemplos comerciais fictícios (D54)

| Produto | Unidade/SKU | Preço BRL | Margem mínima na chegada |
|---|---|---:|---:|
| Farinha de mandioca | pacote de 500 g | R$ 18,00 | 30 dias |
| Castanha-do-pará | pacote de 200 g | R$ 28,00 | 30 dias |
| Chocolate 70% | barra de 80 g | R$ 22,00 | 45 dias |
| Cacau em pó | pacote de 200 g | R$ 24,00 | 60 dias |
| Cuia decorativa | 1 peça | R$ 45,00 | não se aplica |
| Cesto de fibra | 1 peça | R$ 75,00 | não se aplica |
| Tigela de cerâmica decorativa | 1 peça | R$ 65,00 | não se aplica |
| Vaso de cerâmica | 1 peça | R$ 95,00 | não se aplica |

Esses valores demonstram regras de software; não são pesquisa de preços nem
orientação de conservação. O peso em gramas dos alimentos é conteúdo líquido,
não peso de envio. Dimensões e peso bruto estão em D57.

## 3. Limites reservados para C29

- Arredondamento, ordem de subtotal/frete/desconto, comportamento quando o
  desconto excede o subtotal e exemplos de centavos.
- Tipos de desconto, mínimo de compra, validade, fuso/instante de expiração e
  combinações de cupons.
- Reserva concorrente do contador global, momento de consumo, liberação após
  falha/expiração e efeito de reembolso integral sobre esse contador.
- A semântica de D34 não autoriza reset ilimitado de limite global; propor e
  revisar regra explícita em C29 antes de codificar.

### Recomendação para revisão em C29

Esta proposta não está aprovada: reservar capacidade global ao criar o pedido
pendente, consumi-la quando o pagamento for confirmado e liberar a reserva se
o pedido expirar ou o pagamento falhar. Após reembolso integral confirmado,
restaurar a elegibilidade por e-mail prevista em D34, mas manter consumido o
limite global histórico do cupom. Assim, reembolso não transforma um limite
global finito em usos ilimitados. Confirmar a regra antes de implementá-la.

## 4. Critérios de aceitação documental

| ID | Critério | Evidência |
|---|---|---|
| PRC-001 | D17/D33/D34/D54 transcritas sem estender o limite do cupom | revisão contra `docs/decisions.md` |
| PRC-002 | Questões de cálculo e reserva global encaminhadas a C29 | seção §3 e matriz de rastreio |
| PRC-003 | Nenhum comportamento de promoção ou combinação inventado | revisão humana da proposta |

## 5. Verificação

```bash
npm run docs:check --prefix frontend
git diff --check
```

Revisar manualmente a correspondência com `docs/decisions.md` e
`specs/SPEC-catalog.md`.
