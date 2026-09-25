# Spec de pedidos — C50

## 0. Metadados

- Módulo: `orders`
- Status: proposta de especificação para revisão; sem implementação
- Decisões base: D11–D13, D17, D29–D31, D33–D34, D37, D64–D66, D68
- Dependências: identity (`SPEC-identity`), pricing (`SPEC-pricing`), shipping (`SPEC-shipping`), eventing (`SPEC-eventing`). `orders` não importa entidades desses módulos: recebe valores/identificadores em comandos próprios.
- Personas: convidado, cliente com conta opcional, administrador

## 1. Objetivo ◆

Registrar cada compra aceita como um **pedido imutável** (o que foi comprado, por
quanto, para onde/como entregar) e uma **história append-only de estados**. O
pedido é a fonte de verdade do que o cliente contratou; pagamento, estoque e
expedição reagem a ele por eventos e nunca reescrevem o snapshot.

Fora de escopo: cálculo de preço (C30), cotação (C42), reserva de estoque (C57),
cobrança (C53+), etiquetas (C70) e notificações (C74). Esta spec define somente
o registro, as transições e quem enxerga o quê.

## 2. Comandos ◆

Alvos futuros (só valem depois de C51/C52 existirem e serem executados):

- `./backend/mvnw -f backend/pom.xml -Dtest='OrderLifecycleTest' test`
- `./backend/mvnw -f backend/pom.xml -Dit.test='OrderLifecycleIT,OrderAccessIT' verify`
- `npm --prefix frontend run contracts:check`

## 3. Estrutura ◆

- `B/orders`: `domain` (agregado `Order`, `OrderStatus`, `OrderTransition`), `application` (comandos de criação/transição, consultas), `adapter/persistence` (repositório JDBC, migration), `adapter/web` (C52).
- `M/`: tabelas `purchase_order`, `purchase_order_item`, `purchase_order_status_history` (nomes finais em C51; `order` é palavra reservada em SQL).
- `contracts/openapi`: `GET /api/v1/orders/{id}`, `GET /api/v1/orders`, `GET /api/v1/admin/orders`, `GET /api/v1/admin/orders/{id}` (C52).
- Não criar: serviço genérico de máquina de estados reutilizável, DTO espelho de entidade de outro módulo, endpoint de edição de snapshot.

## 4. Estilo e convenções ◆

- Dinheiro em centavos `long` e moeda `BRL` (D17); datas `Instant` UTC; identificadores UUID.
- Estados e razões são enums em inglês maiúsculo; mensagens ao cliente ficam na interface.
- Erros como Problem Details com `codigo` `ORDER_NNN` e `correlationId`.
- Todo texto de produto/endereço/ponto de retirada preserva o rótulo de demonstração (D65, D22).

## 5. Estratégia de testes ◆

| Critério | Teste | Onde |
|---|---|---|
| ORD-001 snapshot imutável | `OrderSnapshotIT.catalogChangeAfterPurchaseDoesNotAlterOrder` | Testcontainers |
| ORD-002 transições válidas/inválidas | `OrderLifecycleTest` (tabela completa origem×destino) | unitário |
| ORD-003 história append-only | `OrderLifecycleIT.updateOrDeleteOfHistoryIsRejectedByDatabase` | Testcontainers |
| ORD-004 rollback de transição | `OrderLifecycleIT.failedTransitionLeavesStatusHistoryAndOutboxUnchanged` | Testcontainers |
| ORD-005 evento na mesma transação | `OrderLifecycleIT.transitionWritesOutboxEventAtomically` | Testcontainers |
| ORD-006 visibilidade | `OrderAccessIT` (convidado com/sem token, cliente dono/outro, admin) | MockMvc + PostgreSQL |
| ORD-007 idempotência de criação | `OrderSnapshotIT.sameCheckoutKeyReturnsSameOrder` | Testcontainers |

Relógio controlado; sem `sleep`; cada transição testa o lado permitido e o lado proibido.

## 6. Limites de atuação ◆

- Esta spec não define como o pagamento é obtido (SPEC-payments, C53) nem os passos de compensação (SPEC-checkout, C56): apenas quais fatos de pedido existem.
- **ORD-Q01 (aberta):** política de retenção de dados pessoais do pedido (endereço/e-mail) depois de concluído. Sem resposta, mantém-se indefinidamente e nada é apagado. Momento: antes de qualquer job de limpeza.
- **ORD-Q02 (aberta):** validade do token de acesso do convidado (proposta: sem expiração enquanto o pedido estiver aberto e 90 dias após conclusão). Momento: C52. Implementação da C52: o token não expira até esta pergunta ser respondida.
- Silêncio não é aprovação: as propostas acima só valem após revisão.

## 7. Regras e invariantes

### 7.1 Snapshot

- R01: o pedido é criado em `PENDING_PAYMENT`, a partir de um snapshot de checkout já validado (versão, fingerprint, total canônico). Itens gravam SKU, nome, unidade, quantidade, preço unitário e subtotal **por valor**, sem referência viva ao catálogo.
- R02: gravam-se também subtotal, frete, desconto (tipo/valor/código normalizado), total, moeda, modalidade (`DELIVERY` ou `PICKUP`, D37), endereço normalizado ou ponto de retirada, prazos prometidos e versão da regra de cálculo.
- R03: nenhuma coluna do snapshot é atualizável depois da criação (trigger ou constraint); correção comercial gera novo estado/evento, não edição.
- R04: `orderId` é gerado no servidor; a criação é idempotente pela chave de checkout (ORD-007): repetir devolve o mesmo pedido.
- R05: o e-mail de contato é o e-mail verificado da compra (D33/D64). Igualdade de e-mail nunca vincula pedido de convidado a uma conta (SPEC-identity §R07/IDN-011).

### 7.2 Estados

`PENDING_PAYMENT`, `PAID`, `PREPARING`, `READY_FOR_PICKUP`, `IN_TRANSIT`, `DELIVERED`, `PICKED_UP`, `CANCELLED`, `EXPIRED`, `UNDER_REVIEW`.

Estados terminais: `DELIVERED`, `PICKED_UP`, `CANCELLED`, `EXPIRED`.

| Origem | Destino | Ator | Precondição | Evento |
|---|---|---|---|---|
| — | `PENDING_PAYMENT` | sistema (checkout) | snapshot válido | `order.created` |
| `PENDING_PAYMENT` | `PAID` | sistema (pagamento confirmado) | reserva ainda válida | `order.status_changed` |
| `PENDING_PAYMENT` | `EXPIRED` | sistema (reserva vencida, D11) | sem pagamento confirmado | `order.status_changed` |
| `PENDING_PAYMENT` | `UNDER_REVIEW` | sistema | pagamento confirmado após expiração da reserva (D13) | `order.status_changed` |
| `PENDING_PAYMENT` | `CANCELLED` | cliente/admin | sem pagamento confirmado | `order.status_changed` |
| `PAID` | `PREPARING` | admin/sistema | preparação iniciada (D24) | `order.status_changed` |
| `PAID`/`PREPARING` | `CANCELLED` | cliente/admin | nenhum pacote entregue à transportadora e retirada não confirmada (D29, D31) | `order.status_changed` |
| `PREPARING` | `READY_FOR_PICKUP` | admin | modalidade `PICKUP` | `order.status_changed` |
| `PREPARING` | `IN_TRANSIT` | sistema (expedição) | modalidade `DELIVERY`; primeiro pacote entregue à transportadora | `order.status_changed` |
| `IN_TRANSIT` | `DELIVERED` | sistema (tracking) | todos os pacotes entregues (D68) | `order.status_changed` |
| `READY_FOR_PICKUP` | `PICKED_UP` | admin (atendente) | retirada confirmada; exclui cancelamento (D31) | `order.status_changed` |
| `READY_FOR_PICKUP` | `CANCELLED` | cliente/admin | antes da confirmação pelo atendente (D31) | `order.status_changed` |
| `READY_FOR_PICKUP` | `UNDER_REVIEW` | sistema | 3 dias úteis sem retirada (D66) | `order.status_changed` |
| `IN_TRANSIT` | `UNDER_REVIEW` | cliente/admin | solicitação de cancelamento com pacote já entregue à transportadora (D30) | `order.status_changed` |
| `UNDER_REVIEW` | destino explícito | admin | decisão registrada com motivo; volta a `PAID`/`READY_FOR_PICKUP`/`IN_TRANSIT` ou vai a `CANCELLED` | `order.status_changed` |

Qualquer par ausente da tabela é rejeitado (`ORDER_002`, 409). Estado terminal não sai. Reembolso é fato de pagamento, não estado do pedido; `CANCELLED` por compensação registra a razão `REFUND_COMPENSATION`.

### 7.3 História

- R06: cada transição grava uma linha `(orderId, sequence, from, to, actor, reason, occurredAt, correlationId)` com `sequence` contígua por pedido; UPDATE/DELETE são bloqueados por trigger.
- R07: estado atual do pedido e a última linha da história mudam na mesma transação, junto com o evento de outbox (`SPEC-eventing` §4.1).
- R08: guarda de retirada (D66) e despacho parcial (D68) não cancelam nem reembolsam automaticamente; abrem `UNDER_REVIEW` ou seguem o fluxo normal.

### 7.4 Visibilidade

| Papel | Lista | Detalhe | Histórico |
|---|---|---|---|
| Convidado com token válido do pedido | não | só aquele pedido | só aquele pedido |
| Convidado sem token | não (401) | 401/403 | 401/403 |
| Cliente autenticado | somente pedidos com seu `accountId` | somente os seus; alheio → 404 | idem |
| Admin | todos, paginado (rota admin) | qualquer | qualquer |

O token é aleatório de alta entropia, guardado como hash, mostrado uma vez na confirmação e enviado por e-mail (C75). Dados de outros clientes nunca aparecem em erro (404 em vez de 403 para pedido alheio).

## 8. Contratos

- Operações: ver §3; todas somente leitura (criação e transições ocorrem por comandos internos do checkout/pagamento/expedição, não por API pública genérica).
- Cabeçalhos: `Cache-Control: private, no-store` em respostas de pedido.
- Erros: `ORDER_001` pedido não encontrado/sem acesso (404), `ORDER_002` transição inválida (409), `ORDER_003` token inválido (401), `ORDER_004` paginação/UUID inválidos (400).
- Prova do convidado: cabeçalho `X-Order-Token`, válido somente para o pedido a que pertence. Listas usam `content/page/size/totalElements/totalPages`.

### Eventos emitidos

| Tipo | schemaVersion | `aggregateId` | Payload mínimo (sem dados pessoais) |
|---|---|---|---|
| `order.created` | 1 | `orderId` | `orderId`, `totalCents`, `currency`, `mode`, `itemCount` |
| `order.status_changed` | 1 | `orderId` | `orderId`, `from`, `to`, `reason`, `sequence` |

`aggregateVersion` é o `sequence` da história (criação = 0). Nenhum evento carrega endereço, e-mail ou token.

## 9. Critérios de aceitação documental

| ID | Critério | Evidência |
|---|---|---|
| ORD-DOC-1 | Snapshot por valor, sem chave viva para catálogo/preço | revisão §7.1 |
| ORD-DOC-2 | Tabela de transições cobre D11–D13, D29–D31, D66 e D68 | revisão §7.2 vs `docs/decisions.md` |
| ORD-DOC-3 | Visibilidade convidado/cliente/admin consistente com IDN-011 | revisão §7.4 vs `SPEC-identity` |
| ORD-DOC-4 | Eventos definidos e sem dados pessoais | revisão §8 vs `SPEC-eventing` |
| ORD-DOC-5 | Módulo não importa entidades de outro módulo | revisão §0/§3 |
