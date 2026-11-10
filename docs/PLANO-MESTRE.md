# De Lá do Pará — plano mestre

Loja única de produtos paraenses para portfólio local, sem custo de serviços.
Spring Boot + Angular, monólito modular (API e worker), PostgreSQL, checkout
confiável com Kafka, Asaas e Melhor Envio em sandbox. Documentação da API e
testes são prioridade.

Este arquivo é o plano vivo e curto. O texto integral de 20/09/2026 (decisões
D01–D70, mapa de capacidades, catálogo, design, plano técnico e as 122 fichas
de tarefa com dependências, aceite e verificação) está em
[archive/plano-mestre-completo.md](archive/plano-mestre-completo.md). Estado de
cada etapa: [tasks/progress.md](../tasks/progress.md) e
[traceability.md](traceability.md). Decisões: [decisions.md](decisions.md);
escopo: [scope.md](scope.md); specs em `specs/`.

## Regras

- Não repetir perguntas já respondidas em `decisions.md`. Proposta não é
  decisão: regra comercial em aberto bloqueia só o comportamento que depende
  dela e nunca vira aprovação por silêncio.
- Dados da demonstração são fictícios. Não alegar produção, segurança
  alimentar nem proteção física de embalagem.
- Código e nomes em inglês; guias e textos da loja em pt-BR. Sem abstração para
  um único uso, código morto, `any` ou erro engolido.
- Não publicar no LinkedIn, fazer deploy em nuvem nem comprar serviço pago.
- Native image GraalVM só em CI; localmente, modo JVM.

## Ciclo por tarefa

1. Pegar o primeiro ID pendente com dependências prontas; ler só a spec e o
   módulo relevantes.
2. Spec antes do código quando houver decisão nova; contrato e exemplos junto.
3. Teste que falha, implementação mínima, gates da seção abaixo.
4. Um commit por intenção (Conventional Commits, ≤72 caracteres), push
   imediato, PR própria. PR empilhada só quando a dependência não foi mesclada.
   Nunca `git add .`, commit vazio ou `Co-Authored-By` de IA.
5. Registrar em `tasks/progress.md` somente após evidência real (comando,
   resultado, SHA, PR, checks). Bloqueio é registrado sem marcar concluído.
6. Merge só com CI 7/7 e autorização do Manito. Sem force-push nem reescrever
   histórico público.

## Convivência entre agentes

- Cada agente usa worktree e branch próprias (`claude/<id>-…`, `codex/<id>-…`);
  o checkout principal não é editado. Sempre `git fetch` e partir de
  `origin/main`.
- Migrations Flyway: próximo número livre em `main` no rebase final; quem
  mesclar depois renumera.
- Arquivos compartilhados (este plano, `tasks/progress.md`,
  `docs/traceability.md`, `contracts/openapi.yaml`, `application.yml`) só
  recebem acréscimos pequenos.
- Tarefa de outra trilha só com troca registrada em PR própria.

| Trilha | Responsável | Áreas |
|---|---|---|
| Release, banco, pagamentos, observabilidade | Claude | `payments`, `checkout`, `eventing`, `F/checkout`, restore |
| Identidade, carrinho, cupons, expedição, design | Codex | `identity`, `cart`, `pricing`, `shipping`, `F/account`, `docs/design` |

## Gates

| Gate | Comando |
|---|---|
| Docs | `npm --prefix frontend run docs:check`, `git diff --check` |
| Backend | `./backend/mvnw -B -f backend/pom.xml verify` (Docker/Testcontainers) |
| Frontend | `npm --prefix frontend run lint`, `format:check`, `test:ci`, `build` |
| Contratos | `npm --prefix frontend run contracts:check` |
| E2E / a11y | `npm --prefix frontend run e2e:local`, `e2e:a11y` |
| Segurança | `scripts/verify.sh security` (npm audit + osv-scanner) |
| Sandbox | roteiro opt-in com Asaas e Melhor Envio reais; só após C04 |

CI exige backend, frontend, contratos, docs, segurança, commit-policy e
quality-gate. PR aberta ou mergeável não conta como integração.

## Status das tarefas

Legenda: ✅ mesclada em `main`. Dependências de cada ID: ficha no arquivo integral.

### Concluídas

| IDs | Entrega |
|---|---|
| C00a–C02, C05–C13c | Repositório, SDD, bootstrap, infra local, gates e CI/CD |
| C14–C16 | Sessões e login de administrador |
| C17–C24 | Catálogo: produtores, produtos, SKUs, imagens e telas admin |
| C25–C28 | Inventário: lotes, ajustes e tela |
| C29–C32 | Preços, cálculo canônico, reserva atômica e admin de cupom (API) |
| C33–C37 | Vitrine pública |
| C38–C40 | Carrinho de convidado |
| C41–C44 | Frete, cotação ligada ao snapshot, checkout de endereço/retirada |
| C45–C49 | Outbox, Kafka, consumidor idempotente, retry e quarentena |
| C50–C52 | Pedidos imutáveis e consulta autorizada |
| C53–C62 | Pagamento: intents, simulador, worker, webhooks, resultado, UI de acompanhamento |
| C64–C69 | Conciliação, `UNKNOWN`, expiração, reembolso, cancelamento e UI |
| C72 | Retirada local |
| C74 | Spec de notificações (proposta) |
| C76–C77 | Verificação e recuperação de conta (API) |
| C82, C82a | Replay autorizado de eventos e conciliação financeira |
| C93 | Upgrade com dados e restore isolado |

### Fora da v1 (corte de escopo de 2026-11-10)

O diferencial do portfólio é o checkout confiável (outbox, Kafka, conciliação,
reembolso, observabilidade), já em `main`. O que dependia de sandbox, de decisão
em aberto ou de muita superfície sai da v1 e fica registrado como evolução.
As fichas continuam no arquivo integral.

| IDs | O que sai | Consequência |
|---|---|---|
| C70, C71, C73, C73a, C82b | Etiquetas, rastreio e operação de expedição | Entrega usa a cotação aceita; retirada local (C72) é o fluxo completo de cumprimento |
| C75, C78, C81b | Notificações e vínculo de compra de convidado | Convidado acompanha o pedido pelo link com token (C52/C62) |
| C80, C81, C81a, C84 | Fusão de carrinho, UI de conta e admin de cupom | Contas ficam só na API (C76/C77); cupom criado pela API admin |
| C03, C04, C63 (SB), C89 | Revisão visual e provas em sandbox real | Pagamento e frete simulados; adapter Asaas fica como código testado por HTTP gravado |
| #173 (C42 ao vivo) | Cotação ao vivo | Cotação simulada determinística |
| C79 (frete/notificações), C83 (etiquetas) | Partes que dependiam dos itens acima | Operações e observabilidade cobrem pagamento e eventos |
| C92 (UIs de conta/expedição) | Escopo reduzido | Release empacota API, worker e vitrine/checkout |

Drafts #134, #135 e #173 e as branches da lane Codex ficam preservados, sem
merge. README e vídeo declaram o que é simulado.

### Fechamento da v1 (ordem)

| ID | Entrega |
|---|---|
| C79, C79a, C83 | Dar por completos para pagamentos e eventos |
| C85 | Isolamento de sessão na API e no SSR |
| C86 | Jornadas de teclado/a11y do cliente e do admin existentes |
| C87 | Compatibilidade de eventos e API |
| C88 | Matriz falha → teste automatizado verificado |
| C90, C91 | Carga de catálogo (#167) e checkout; orçamentos medidos (precisa de `k6`) |
| C92a | Artefatos de release reproduzíveis com smoke |
| C94 | Recuperação após banco antigo, só pagamentos/eventos |
| C95 | Bootstrap validado a partir de clone limpo |
| C96, C96a, C96b | Trade-offs, roteiro e vídeo |
| C97, C98 | Checklist final e primeira release local |

## Riscos

- Dependência de sandboxes externos: tudo que toca provedor roda por adapter e
  simulador; C63/C70 ficam parciais até C04.
- Pagamento ambíguo: nunca tratar timeout como falha; `UNKNOWN` é conciliado.
- Estoque e cupom sob concorrência: reserva por constraint e lock curto, sem
  transação aberta esperando provedor ou Kafka.
- Vazamento de PII: nada de CPF, tokens ou URLs de pedido em log, payload ou
  evidência.
