# Matriz de rastreio — requisito → spec → teste → commit → evidência

Criada em C02; atualizada a cada entrega, somente após evidência verificada.
Planejamento aprovado não significa teste aprovado. Sem tokens, CPF, senhas
ou payloads pessoais.

| Requisito / tarefa | Spec | Teste / verificação | Commit(s) | Evidência |
|---|---|---|---|---|
| C00a — repo e pré-requisitos | Plano §2 | `ls`, `git status`, versões de ferramentas | `7437919` (parte) | `tasks/progress.md` sessão bootstrap |
| C00b — plano no clone | Plano §2 | `git remote -v`, `sha256sum` idêntico (`c67d245e…`) | `7437919` | `docs/PLANO-MESTRE.md` + push `main`, repo PUBLIC |
| C01 — escopo e mapa | `docs/scope.md` | Revisão automatizada: D 64/64, A 10/10, módulos 12/12, figuras 26/26; `git diff --check`; PR CLEAN/MERGEABLE | `435487c`, merge `b9e70f8` | PR #1 merged; `tasks/progress.md` sessão C01 |
| C02 — convenções SDD/ADR | `docs/contributing.md` (+ templates) | DOC: links, `git diff --check`, exemplo preenchido | `c74ba24`, merge `8584626` | PR #2 merged |
| C03 — design e jornada | `docs/design/brief.md` | DOC: paleta/fontes/WCAG, storyboard Q01 | `87a05bd` | PR #3 merged |
| C04 — integrações sandbox | `docs/integrations/*` | DOC: Asaas/Melhor Envio, homologation Cloudflare | `1911c83` | PR #4 merged (sync `2c287a6`) |
| C05 — arquitetura | `docs/adr/*` | DOC: ADRs 0001–0005 | `298f4d9` | PR #5 merged |
| C06 — backend bootstrap | — | `./mvnw verify` contextLoads, Boot 4.1.1, Java 25 | `b7354fe` | PR #6 merged |
| C07 — frontend bootstrap | — | `ng test`/`build` SSR, strict | `13664fc` | PR #7 merged |
| C08 — infra local | `docs/local-guide.md` | `compose.yml` healthy 4/4, PG/Kafka reais | `150630a` | PR #8 merged |
| C09 — gates backend | — | `verify` unit 4/4 IT 2/2, ArchUnit 3/3 | `ff5097c` | PR #9 merged |
| C10 — gates frontend | — | lint 0, `test:ci` 4 passed, Playwright 1 passed | `707a26b` | PR #10 merged |
| C11 — contratos | `contracts/openapi/v1.yaml` | `contracts:check` generate+tsc | `d7b1afd` | PR #11 merged |
| C11a — referência API | `docs/api-guide.md` | Redoc 40KB, stub WireMock | `0cac71d` | PR #12 merged |
| C11b — harness contrato | `RouteContractCoverageTest` | `verify` + fixtures divergentes reprovam | `37f86ec` | PR #13 merged |
| C14 — spec identity | `specs/SPEC-identity.md` | DOC: `docs:check` 24 OK, `verify docs/security` OK, CI 7/7 | `4b82a3f`, merge `2edd714` | PR #26 merged (`35548577157` success) |
| C17 — spec catálogo/procedência | `specs/SPEC-catalog.md` | local `docs:check` 24 OK, `contracts:check` exit 0; CI 7/7 success (`35631089081`) | `4c424dc`; PR #30 merged (`87623af`) | Spec aprovada pelo usuário em 2026-09-21; CAT-Q01 respondida (só admin gere produtores, sem conta v1); C18 liberada |
| C18 — persistência de produtores | `specs/SPEC-catalog.md`, migration V13 | `ProducerPersistenceIT`, `ProducerRepositoryIT`; PostgreSQL 18.6/Testcontainers; Flyway V13 + Hibernate validate | PR #31 merged (`bbd7a0c`) | UUID estável, slug único, origem/descrição, atualização verificados; publicação e CI remoto concluídos |
| C19 — API administrativa de produtores | `contracts/openapi/v1.yaml`, `docs/api-guide.md` | `ProducerAdminApiIT`; contrato+geração Angular; 401/403/CSRF/400/404/409/201/200/405 | PR #31 merged (`bbd7a0c`) | CAT-Q01–03 aplicadas; só admin; API sem DELETE, com flag de demonstração; publicação e CI remoto concluídos |
| C20 — UI administrativa de produtores | `specs/SPEC-catalog.md` §6–7, decisão D65 | `admin.component.spec.ts` (carregamento/criação); Playwright `admin-producers.spec.ts` (criar, editar, desativar, recarregar) | PR #31 merged (`bbd7a0c`) | Conteúdo claramente demonstrativo; sem botão de exclusão; perfil inativo e referências persistem; publicação e CI remoto concluídos |
| C21 — persistência de produtos e SKUs | `specs/SPEC-catalog.md` §§3–7, D17–D18/D27/D54/D57/D65 | `ProductCategoryInvariantTest`, `ProductSkuPackagingTest`, `ProductRepositoryIT`; PostgreSQL 18.6/Testcontainers + Flyway V14 + Hibernate validate | PR #33 merged (`9dc3452`) | Dois SKUs de conteúdos distintos ligados ao produto; duplicidade, validade/conteúdo e dimensão inválidos rejeitados; desativação preserva IDs, ligações e atributos SKU; snapshots independentes pertencem a pedidos |
| C22 — API de administração e consulta de produto | `specs/SPEC-catalog.md` §§7–10, OpenAPI `Product*` | `ProductAdminApiIT`; PostgreSQL 18.6/Testcontainers + Flyway V15; verificação de contrato e cobertura de rotas | PR #34 merged (`93bd6b4`) | Criar/editar/listar/consultar; sem DELETE; contrato/geração TS aprovados; FOOD/CRAFT, autorização/CSRF, 404/409 e filtros públicos verificados; CI 7/7 verde |
| C23 — UI administrativa de produtos | `specs/SPEC-catalog.md` §§7–9, API C22 | `product-admin.component.spec.ts`; Playwright `admin-products.spec.ts`; `scripts/verify.sh frontend` | PR #35 merged (`bd36dc4`) | Alimentos/artesanato, produtores ativos, variantes, embalagem, conflito recuperável e confirmação ao desativar; 10 testes Angular e 3 Playwright; CI 7/7 verde |
| C24 — imagem principal de produto | `specs/SPEC-media.md`, ADR 0006 | Backend verify, contrato, docs/security/frontend gate e `aislop` | PR #36 merged (`b66326b`) | Uma imagem principal, JPEG/PNG até 5 MiB, validação de bytes, associação PostgreSQL, substituição/remoção, autorização/CSRF e estados públicos; CI `35656338034` 7/7 verde |
| C24a — políticas de logística, cancelamento e cupons | `specs/SPEC-logistics.md`, `specs/SPEC-pricing.md` (base C29) | `docs:check`, `git diff --check`, revisão humana e conferência D21–D67 | branch `docs/c24a-logistics-decisions`, proposta ainda não commitada | Respostas consolidadas; D66 guarda de retirada e D67 fixture de lotes aprovadas; recomendações de despacho parcial e contador global aguardam pergunta individual |
| C25 — especificação de inventário | `specs/SPEC-inventory.md`, D11/D13/D27/D54/D66/D67 | `docs:check`, `git diff --check`, revisão humana da spec; implementação/testes em C26/C57 | PR #37 merged (`c8a97d8`) | Vocabulário de saldos, reserva de 15 minutos, elegibilidade por chegada, fixture sintética e INV-001–008 documentados; FEFO/bloqueio ativo/calendário anual ainda abertos; merge não substitui a revisão humana pendente |
| C29 — especificação de preço e cupons | `specs/SPEC-pricing.md`, D17/D33/D34/D54 | `docs:check`, `git diff --check`, revisão humana; BT em C30 e BI em C31 | PR #37, commit `a819ffd` + atualização local | Centavos inteiros, ordem subtotal/frete/desconto, arredondamento proposto, mínimos/validade/e-mail verificado e reembolso documentados; reserva global e combinações ainda abertas; não concluído |
| Plano C30/C31 | `tasks/plan.md`, `specs/SPEC-pricing.md` | revisão de dependências e checkpoints | `2151a0e` | Ordem C30 → C31, testes BT/BI, gates e bloqueios de contador global documentados |
| C30 — totais canônicos | `backend/src/main/java/br/com/deladopara/pricing/domain`, `backend/src/test/java/br/com/deladopara/pricing/domain` | `PurchaseTotalCalculatorTest`, backend `verify`, CI 7/7 | PR #38 merged (`eb5e4d5`) | Cálculo puro em centavos BRL, moeda estrangeira e mínimo rejeitados; contador global e API de checkout permanecem fora do escopo |
| C26–C28 — estoque, ajustes e UI administrativa | `specs/SPEC-inventory.md`, OpenAPI | testes de lote/ledger/reserva, `InventoryPersistenceEntityTest`, API administrativa e frontend de inventário; backend/frontend CI | PR #39 merged (`b251da4`) | Saldos, margem D67, bloqueio, ledger idempotente, reserva temporal, intenção multi-SKU, ajustes com ator/motivo/versão e tela administrativa de lotes/histórico; FEFO e alocação comercial permanecem fora deste slice |
| C45 — contrato de eventing | `specs/SPEC-eventing.md`, ADR-0002, `contracts/events/envelope.schema.json` | `docs:check`, `contracts:check`, revisão humana; testes de queda em C46–C49 | PR #65 merged (`5cd6104`); CI `35775796708` passou 7/7 | Outbox, claims/leases, ACK, deduplicação por `eventId+handler`, ordem por agregado, quarentena e replay documentados; a spec continua em revisão do usuário: valores de lease/backoff/tentativas/retenção são propostas e C04 aguarda homologação real; sem promessa global `exactly-once` |
| C46 — persistência transacional da outbox | `backend/src/main/java/br/com/deladopara/eventing`, migration Flyway V24, `SPEC-eventing` | `OutboxEventTest`, `OutboxEventPersistenceIT`, backend verify, Spotless e `aislop` | PR #66 merged (`116f612`) | Envelope versionado/correlação em JSONB; writer exige transação existente; commit e rollback do efeito local e da outbox permanecem atômicos; publisher/consumers/retries fora do escopo; CI `35781856327` verde |
| C47 — primeira fatia do publisher Kafka | `specs/SPEC-eventing.md` §§4.1/5 | `OutboxPublisherTest`, `KafkaOutboxEventBrokerTest`, `OutboxPublisherPersistenceIT`, backend verify | PR #68 merged (`b07ffbc`); CI `35786482536` verde | Claim PostgreSQL com lease/`SKIP LOCKED`, chave `aggregateId`, envelope canônico, ACK síncrono e `PUBLISHED` posterior; reinício/queda e parâmetros operacionais ainda pendentes |
| C48 — consumo idempotente | `SPEC-eventing` §4.2, migration V25, `EventConsumptionService`, `KafkaEventConsumer` | `EventConsumptionPersistenceIT`, `EventConsumptionServiceIT`, `KafkaEventConsumerIT`, `EventingWorkerConfigIT` (Kafka/PostgreSQL reais) | verificado localmente; sem PR | `eventId+handler` único, efeito e recibo no mesmo commit, offset só após commit PostgreSQL; nenhum handler comercial habilitado |
| C49 — retry e quarentena | `SPEC-eventing` §5, migration V26, `EventRetryPolicy`, `EventFailureService` | `EventRetryPolicyTest`, `EventFailureServiceIT`, `KafkaEventConsumerIT`, `EventingWorkerConfigIT` (quarentena com Kafka real, evento válido seguinte processado) | verificado localmente; sem PR | valores C45 aprovados aplicados; diagnóstico sanitizado; retenção/limpeza da quarentena e replay operacional pendentes |
| C31 — reserva atômica de cupom | `SPEC-pricing` §§4–5, migration V27, `CouponReservationService` | `CouponReservationServiceIT` (PostgreSQL real, corrida de 12 threads, chave idempotente, transições) | verificado localmente; sem PR | limite global e por e-mail respeitados sob concorrência; regra de reembolso (global permanece gasto) é a recomendação ainda não aprovada da spec |

Evidências por release vivem em `docs/evidence/<marco-ou-release>/`
(criado quando houver a primeira entrega executável).

## Observabilidade e CI

| Requisito / tarefa | Spec | Teste / verificação | Commit(s) | Evidência |
|---|---|---|---|---|
| C12 / OBS-001–003 | `docs/observability.md` | `RequestCorrelationFilterTest`, `HealthEndpointTest`, verify + JAR/JSON reais | `07b469a` | `tasks/progress.md` sessão C12/C13 |
| Correção de lint após C11a | Gates C10/C11a | Lint com `api-reference/index.html` existente; F/E verdes | `679c3bc` | Falha ICU antes da exclusão; não exclui templates da aplicação |
| C13 — CI inicial | `docs/ci.md` | `scripts/verify.sh`, jobs backend/frontend/contracts/quality-gate | `6c7556d` | PR #14 merged (`35547099486`) |
| C13a — docs gate | `docs/ci.md` | `check-docs.mjs` + `contracts:check` | `5c099a6`, merge `d2fb069` | PR #15 merged (`35547722865`) |
| C13b — security gate | `docs/ci.md` | `check-secrets.sh` + audit runtime 0 high | `b84a01d`, merge `d0ce796` | PR #16 merged |
| C13c — quality gate | `docs/ci.md` | `check-commits.sh` + `quality-gate` 7/7, proteção `strict:true` | `071315e`+`68faf7e`, merge `2db8139` | PR #25 merged (`35548287393`) |
