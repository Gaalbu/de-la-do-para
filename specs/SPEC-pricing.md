# Spec de preço e cupons — C29

## 0. Metadados

- Módulo: `pricing`
- Status: proposta de especificação para revisão; sem implementação
- Decisões base: D17, D33–D34, D54
- Dependências: catálogo (`catalog`); pedidos/checkout consomem snapshots calculados

## 1. Objetivo

Definir cálculo determinístico de subtotal, frete, desconto e total, além da
elegibilidade de cupons para convidados. O servidor é a fonte do preço; o
navegador envia somente intenção e código de cupom. Cálculos não fazem chamada
externa nem alteram estoque.

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

## 3. Modelo monetário e cálculo

- Todos os valores públicos são BRL em centavos inteiros (`long`/inteiro no
  contrato). Moeda diferente, escala fracionária ou valor negativo de entrada
  é rejeitado; frete zero é permitido.
- O preço unitário vem do snapshot vigente do SKU no servidor. Para cada linha,
  calcular `lineTotal = unitPriceCents × quantity`; quantidade deve ser inteira
  positiva e o produto da multiplicação deve ser verificado contra overflow.
- `subtotal = soma(lineTotal)` em ordem estável de linhas. Não arredondar
  durante a soma, pois já se trabalha em centavos.
- `shippingCents` vem do contrato de shipping já cotado e com fingerprint;
  pricing não recalcula ou confia em valor enviado pelo navegador.
- Recomendação de desconto percentual: calcular sobre o subtotal elegível,
  multiplicar por inteiro e dividir por 100 com arredondamento half-up para
  centavos. Recomendação de desconto fixo: limitar a `min(requested,
  subtotalElegible)`; nunca deixar subtotal ou total negativos.
- Ordem proposta: validar código e mínimo contra subtotal elegível → calcular
  desconto → `goodsAfterDiscount = subtotal - discount` → somar frete →
  `grandTotal = goodsAfterDiscount + shippingCents`. Um único cupom por compra
  na v1; combinações ficam proibidas até decisão explícita.
- Snapshot final grava preço unitário, quantidades, subtotal, frete, tipo/valor
  do desconto, total, moeda, versão da regra e código normalizado. Alterar o
  catálogo depois não recalcula pedido existente.

### Exemplos de fronteira propostos

| Caso | Entrada | Resultado esperado |
|---|---|---:|
| Sem cupom | subtotal R$ 18,00; frete R$ 7,50 | total R$ 25,50 |
| Percentual inteiro | subtotal R$ 28,00; 10% | desconto R$ 2,80; subtotal após desconto R$ 25,20 |
| Percentual com centavo | subtotal R$ 18,01; 15% | desconto half-up de R$ 2,70; subtotal após desconto R$ 15,31 |
| Fixo maior que subtotal | subtotal R$ 18,00; cupom R$ 25,00 | desconto limitado a R$ 18,00; mercadoria R$ 0,00 |
| Mínimo não atingido | subtotal R$ 18,00; mínimo R$ 20,00 | cupom inelegível; erro comercial estável |
| Moeda inválida | preço ou frete em USD | rejeitar antes do cálculo |

Os exemplos são critérios de cálculo propostos; o comportamento de arredondamento
e o conjunto final de tipos de cupom precisam ser revisados antes de C30.

## 4. Elegibilidade e ciclo do cupom

- Código é normalizado para comparação (por exemplo, maiúsculas e espaços
  externos), mas o valor exibido no pedido conserva o código apresentado.
- Cupom deve estar ativo, dentro do intervalo de validade no fuso comercial,
  atingir o mínimo configurado e respeitar o tipo de cliente autorizado.
- Convidado usa e-mail verificado; conta opcional usa a identidade autenticada
  e também deve manter e-mail verificado quando a política exigir. Não inferir
  limite por pessoa a partir de e-mail (D33).
- Consulta de preço é somente leitura. Reserva e consumo do contador são
  operações posteriores, atômicas e idempotentes (C31); uma tentativa que
  falhar não pode marcar uso consumido.
- D34: uso associado ao e-mail só volta a ser elegível após reembolso integral
  confirmado. Reembolso solicitado, pendente ou desconhecido não libera.

## 5. Limites reservados para revisão

- Arredondamento, ordem de subtotal/frete/desconto, comportamento quando o
  desconto excede o subtotal e exemplos de centavos.
- Tipos de desconto, mínimo de compra, validade, fuso/instante de expiração e
  combinações de cupons.
- Reserva concorrente do contador global, momento de consumo, liberação após
  falha/expiração e efeito de reembolso integral sobre esse contador.
- A semântica de D34 não autoriza reset ilimitado de limite global; propor e
  revisar regra explícita em C29 antes de codificar.

### Recomendações ainda não aprovadas

Esta proposta não está aprovada: reservar capacidade global ao criar o pedido
pendente, consumi-la quando o pagamento for confirmado e liberar a reserva se
o pedido expirar ou o pagamento falhar. Após reembolso integral confirmado,
restaurar a elegibilidade por e-mail prevista em D34, mas manter consumido o
limite global histórico do cupom. Assim, reembolso não transforma um limite
global finito em usos ilimitados. Confirmar a regra antes de implementá-la.

## 6. Critérios de aceitação documental

| ID | Critério | Evidência |
|---|---|---|
| PRC-001 | D17/D33/D34/D54 transcritas sem estender o limite do cupom | revisão contra `docs/decisions.md` |
| PRC-002 | Valores em centavos, ordem de cálculo e arredondamento são determinísticos | exemplos da seção §3 e testes BT em C30 |
| PRC-003 | Mínimo, validade, e-mail verificado e estados de reembolso têm resultado definido | testes de elegibilidade em C31 |
| PRC-004 | Reserva global e combinações não são decididas silenciosamente | seção §5 e revisão humana |

## 7. Verificação

```bash
npm run docs:check --prefix frontend
git diff --check
```

Revisar manualmente a correspondência com `docs/decisions.md` e
`specs/SPEC-catalog.md`.
