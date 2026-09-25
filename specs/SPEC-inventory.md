# Spec de inventário — C25

## 0. Metadados

- Módulo: `inventory`
- Status: rascunho de trabalho; não autoriza implementação até revisão da spec
- Tarefas: C25 (especificação), C26 (lotes e ledger), C27 (API administrativa), C28 (UI), C57 (reserva atômica)
- Decisões base: D11, D13, D17, D27, D54, D66, D67, D68
- Depende de `catalog`; checkout, carrinho, shipping e storefront consomem somente contratos públicos
- Pendências relacionadas: validade em retirada tardia/calendário; a continuidade dos pacotes após despacho parcial foi aprovada em D68

## 1. Objetivo

Definir lotes por SKU, quantidades físicas, reservas, saldo elegível para uma
data de chegada, bloqueios e ajustes rastreáveis. Impedir venda acima do
estoque, alocação de lote vencido ou insuficiente e liberação duplicada de
reserva. Os dados de demonstração continuam fictícios; esta spec não é
orientação de conservação de alimentos nem prova de disponibilidade física.

## 2. Comandos e estrutura

Verificação documental nesta tarefa:

```bash
npm run docs:check --prefix frontend
git diff --check
```

Estrutura planejada:

- `backend`: agregado e persistência do módulo `inventory`, migrations e
  testes unitários/integração PostgreSQL (C26/C57).
- `contracts/openapi/v1.yaml`: API administrativa de estoque em C27; não
  expor entidades JPA nem rotas durante C25.
- `frontend`: administração e consulta pública em C28/storefront.
- `specs/SPEC-inventory.md`: invariantes, fronteiras e cenários desta
  capacidade.
- Os SKUs e suas margens vêm de `catalog`; o módulo não copia nome, preço,
  mídia ou embalagem do produto.

## 3. Vocabulário e saldo

As quantidades são inteiros não negativos de unidades/SKU (D17). Cada lote
pertence a um SKU e mantém histórico, mesmo quando vencido, bloqueado ou sem
saldo.

| Medida | Definição |
|---|---|
| `physical` | Unidades presentes no ponto local e ainda não despachadas/baixadas; inclui unidades alocadas e unidades bloqueadas/vencidas |
| `reserved` | Unidades físicas alocadas a reservas ativas de checkout ou a pedidos pagos aguardando entrega/retirada |
| `free` | `physical - reserved`; nunca negativo |
| `availableFor(sku, arrivalDate)` | Soma do `free` apenas em lotes não bloqueados e elegíveis para o SKU na data pedida |

Lote bloqueado ou vencido continua no saldo físico até baixa administrativa,
mas contribui com zero para `availableFor`. Consulta sem uma data de chegada
pode informar estoque potencial (`free` em lote ativo), mas não garante que
uma compra com entrega futura será elegível.

## 4. Invariantes

1. Para cada lote: `physical >= 0`, `reserved >= 0` e `reserved <= physical`.
   Para cada SKU, saldos são somas dos lotes; não se grava saldo negativo.
2. Reserva de checkout dura 15 minutos desde sua criação (D11). Seu vencimento
   é um instante UTC exato; `now >= expiresAt` já expirou. A comparação usa
   relógio injetável.
3. Pagamento confirmado antes do vencimento converte a alocação temporária em
   alocação comprometida; as unidades continuam reservadas até expedição,
   retirada confirmada ou cancelamento integral autorizado.
4. Pagamento confirmado depois do vencimento segue D13: encaminhar para análise
   e compensação por reembolso. Não ressuscitar a reserva nem retirar unidades
   que possam ter sido vendidas a outra pessoa.
5. Uma intenção que contém vários SKUs é reservada inteira ou não é reservada.
   Falta ou conflito em qualquer linha desfaz toda a transação/alocação.
6. Reserva, confirmação, expiração, liberação e baixa por expedição são
   idempotentes por referência estável da operação. Repetir o fato não cria
   uma segunda movimentação nem libera quantidade de outra reserva.
7. Lock de concorrência é adquirido numa ordem estável de SKU/lote definida
   pela persistência, sem abrir transação durante chamadas HTTP ou espera de
   Kafka. Constraints do PostgreSQL protegem saldo mesmo com workers concorrentes.
8. Solicitação de cancelamento não altera estoque. Só um fato de cancelamento
   integral autorizado libera alocações ainda não despachadas. Após despacho
   parcial, os demais pacotes continuam o fluxo normal por padrão; pausar exige
   decisão administrativa, sem cancelamento ou reembolso automático ou parcial
   (D68). Pedido pendente ou pedido de análise nunca libera saldo.
9. Na entrega física do pacote à transportadora, baixar somente as unidades
   que pertencem àquele pacote; etiqueta emitida não baixa estoque (D29). Na
   retirada, baixar somente após confirmação exclusiva do atendente (D31).
10. Ajuste requer ator administrador, motivo e evento imutável. Redução que
    ultrapasse `free` é rejeitada; não pode reescrever reserva ativa nem apagar
    movimentações anteriores. Diminuição por vencimento/descarte exige ação
    administrativa explícita, não acontece só pela passagem do tempo.

## 5. Lotes e validade

- Lote alimentar requer `expiresOn`; SKU de artesanato não recebe validade
  alimentar. Datas são datas civis, sem horário; a data prevista de chegada
  (`arrivalDate`) é fornecida por contrato de shipping/checkout.
- Para alimento, calcular dias restantes como
  `DAYS.between(arrivalDate, expiresOn)`. O lote é elegível quando esse valor
  é maior ou igual ao mínimo configurado no SKU (D27/D54). Assim, exatamente
  a margem passa e um dia abaixo falha, conforme fixture sintética aprovada
  em D67. A data de validade registrada nunca é alterada para compensar prazo
  de preparação/transporte.
- A fixture de teste usa relógio fixo e lotes explicitamente fictícios. Para
  cada alimento, cobre a margem exata e margem menos um dia; os valores não
  são estoque comercial inicial (D67).
- Para retirada, `arrivalDate` deve ser a data de disponibilização informada
  pelo fluxo de pedido. O efeito de retirada tardia sobre validade continua
  pendente na spec de shipping/orders; não tratar o prazo de guarda D66 como
  garantia de validade no último dia.
- Lote vencido não recebe novas alocações. A baixa física do vencido é ajuste
  administrativo auditado. Se o lote vencer enquanto estiver comprometido,
  exigir análise explícita; não trocar lote ou cancelar pedido silenciosamente.
- **Estratégia de alocação aprovada (D69):** FEFO (primeiro a vencer,
  primeiro alocado), com desempate por recebimento e UUID estável. Só ordenar
  entre lotes que já passaram validade, bloqueio e saldo; FEFO não substitui
  os limites de validade do SKU.

## 6. Bloqueio e ajuste administrativo

- Bloquear lote impede nova reserva e o remove de `availableFor`, sem apagar
  quantidade física nem histórico. Desbloquear é operação explícita,
  autenticada no servidor, com ator, motivo e versão concorrente (API/UI em
  C27/C28).
- **Regra aprovada para lotes com alocação ativa (D70):** manter as linhas reservadas,
  impedir sua expedição e abrir pendência administrativa; não liberar nem
  realocar unidades até resolução explícita. A revisão deve decidir se o
  operador pode substituir o lote por outro elegível ou precisa cancelar a
  alocação/pedido antes.
- Recebimento aumenta `physical`; expedição confirmada reduz `physical` e
  `reserved` da alocação correspondente; reserva/liberação alteram apenas
  `reserved`. Movimentos carregam tipo, deltas, referência idempotente, ator
  quando humano, instante e justificativa aplicável.
- Ajuste por contagem pode somar/subtrair o saldo somente se o resultado
  continuar não negativo e não menor que o total reservado. Se a contagem real
  for abaixo das reservas, bloquear a correção automática e encaminhar para
  reconciliação administrativa de pedidos/alocações.

## 7. Alocação proposta

1. Receber SKU, quantidade, `arrivalDate` e referência de compra; consultar
   regras/margem do SKU por contrato público de catálogo.
2. Filtrar lotes não bloqueados, não vencidos, elegíveis à data e com saldo
   livre; ordenar segundo a política aprovada de lotes.
3. Adquirir locks em ordem determinística, revalidar validade/bloqueio/saldo
   sob lock e dividir a quantidade entre lotes se necessário.
4. Reservar atomicamente todos os SKUs por 15 minutos. Se faltar uma unidade,
   não deixar reserva parcial.
5. Confirmar para alocação comprometida com pagamento autorizado antes do
   prazo; caso contrário, expirar/liberar uma única vez. Pagamento tardio
   segue D13, sem nova alocação automática.
6. Baixar unidades alocadas no fato de handoff à transportadora ou confirmação
   de retirada, sem baixar no momento de emitir etiqueta ou marcar pacote
   apenas como pronto.

## 8. Estratégia de testes e critérios

Datas e instantes usam `Clock` controlado; PostgreSQL/Testcontainers cobre
constraints, isolamento e concorrência. Não usar sleeps arbitrários nem aceitar
sucesso genérico como prova de reserva atômica.

| ID | Critério | Teste previsto |
|---|---|---|
| INV-001 | Saldos respeitam `physical >= reserved >= 0`; `free` nunca é negativo | teste unitário de invariante e constraint PostgreSQL |
| INV-002 | Limite de validade aceita exatamente D54 e rejeita um dia abaixo | teste com fixture D67 para cada alimento, `Clock` fixo |
| INV-003 | Artesanato não exige validade alimentar; bloqueado/vencido não recebe reserva | teste unitário e integração de repositório |
| INV-004 | Reserva dura 15 minutos; no vencimento ou após, libera uma vez | teste de fronteira `expiresAt - ε`, `expiresAt`, `expiresAt + ε`; PostgreSQL |
| INV-005 | Pagamento anterior confirma alocação; posterior à expiração vai para análise/compensação | teste de aplicação ligado a D13, sem re-reserva |
| INV-006 | Carrinho multi-SKU aloca tudo ou nada sob concorrência | BI com PostgreSQL real; duas transações disputam a última unidade |
| INV-007 | Ajuste exige ator/motivo, preserva ledger e não reduz abaixo de reservas | BI de ledger; segundo ajuste preserva primeiro movimento |
| INV-008 | Handoff baixa somente linhas do pacote e pedido de cancelamento não libera saldo | teste de contrato entre inventory e shipping/checkout; após despacho parcial, os demais pacotes continuam por padrão e pausa exige decisão administrativa, sem cancelamento ou reembolso automático ou parcial (D68) |

## 9. Limites e decisões abertas

- C24a ainda depende das decisões de calendário e validade tardia; a continuidade
  dos pacotes após handoff parcial está definida em D68. Inventory mantém as
  alocações comprometidas até fatos autorizados de shipping/checkout.
- FEFO (D69) e tratamento de bloqueio com alocação existente (D70) foram
  aprovados em 2026-09-24 e estão implementados na C57.
- Calendário anual concreto e lote/datas de operação são distintos das
  fixtures de teste. Configuração deve ser versionada/local, sem API paga,
  usar feriados confirmados em fonte oficial e manter ponto facultativo
  opt-in (D26). A lista municipal anual ainda precisa ser verificada antes
  de fixture real de datas.
- D66 define três dias úteis de guarda e análise manual, mas validade mínima
  no último dia de retirada segue pendente para `orders`/`shipping`.
- Não define API, UI, lote inicial em produção, previsão de frete, regras de
  promoção/cupom, ajuste de reembolso parcial nem política real de conservação.

## 10. Revisão

Antes de C26, revisar: definições de saldo, transições de 15 minutos, fórmula
de dias restantes, alocação indivisível, ajustes auditados e propostas abertas
de FEFO/bloqueio. Decisões abertas devem ser apresentadas uma por vez conforme
`docs/PLANO-MESTRE.md`; regra pendente bloqueia somente o comportamento que
depende dela.
