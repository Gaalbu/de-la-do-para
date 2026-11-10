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

Legenda: ✅ mesclada em `main` · 🟡 parcial · 🚧 PR/branch aberta · ⛔ bloqueada ·
⬜ pendente. Dependências de cada ID: ficha no arquivo integral.

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

### Em andamento ou parcial

| ID | Estado | Falta |
|---|---|---|
| C63 | 🟡 adapter Asaas mesclado | evidência sandbox dos dois meios (C04) |
| C79, C79a | 🟡 métricas, traces e painéis | instrumentação de frete e notificações (C70, C75) |
| C83 | 🟡 pagamentos incertos e eventos | etiquetas (C82b) |
| C90 | 🚧 #167 rascunho, carga de leitura do catálogo | carga de checkout |
| C92, C92a | 🟡 imagens e smoke | UIs de conta e expedição (C73, C81) |
| C42, C43 | 🚧 #173 rascunho, cotação sandbox | validação C04 |
| C70, C71 | 🚧 drafts #134/#135 | manifesto por pacote, mapeamento pacote↔etiqueta (C04) |

### Pendentes por bloqueio

| IDs | Bloqueio |
|---|---|
| C03 | Revisão visual do Manito |
| C04, C45 (homologação), C89 | Credenciais de sandbox Asaas e Melhor Envio |
| C24a, C25, C29 (políticas) | Revisão das propostas das specs (FEFO, combinação de cupons, contador global) |
| C75, C78, C81b | Decisões A13 e ORD-Q02 |
| C73, C73a, C82b | Dependem de C70/C71 |
| C80, C81, C81a, C84 | Lane Codex: carrinho autenticado, UI de conta, admin de cupons |
| C85–C88, C90–C91 | Dependem de C70, C79, C82b |
| C94 | Depende de C70 e C82b |
| C95–C98 | Fechamento: clone limpo, portfólio, vídeo, checklist e release |

## Ordem restante

1. Destravar entradas do Manito: C03, C04 (contas sandbox), políticas de cupom e
   A13/ORD-Q02.
2. Lane Codex: C70 → C71 → C73/C73a/C82b, e C80 → C81 → C81a.
3. Lane Claude: fechar C79/C79a e C90 → C94 → C85–C88 → C91.
4. Fechamento: C92/C92a, C95 → C96/C96a/C96b → C97 → C98.

## Riscos

- Dependência de sandboxes externos: tudo que toca provedor roda por adapter e
  simulador; C63/C70 ficam parciais até C04.
- Pagamento ambíguo: nunca tratar timeout como falha; `UNKNOWN` é conciliado.
- Estoque e cupom sob concorrência: reserva por constraint e lock curto, sem
  transação aberta esperando provedor ou Kafka.
- Vazamento de PII: nada de CPF, tokens ou URLs de pedido em log, payload ou
  evidência.
