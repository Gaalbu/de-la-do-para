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
| C17 — spec catálogo/procedência | `specs/SPEC-catalog.md` | local `docs:check` 24 OK, `contracts:check` exit 0; CI 7/7 success (`35631089081`) | `4c424dc`; PR #30 aberto, mergeable (não merged) | Spec aprovada pelo usuário em 2026-09-21; CAT-Q01 respondida (só admin gere produtores, sem conta v1); C18 liberada |
| C18 — persistência de produtores | `specs/SPEC-catalog.md`, migration V13 | `ProducerPersistenceIT`, `ProducerRepositoryIT`; PostgreSQL 18.6/Testcontainers; Flyway V13 + Hibernate validate | local `feat/c18-producer-persistence` | UUID estável, slug único, origem/descrição, atualização verificados; trabalho pendente de publicação |
| C19 — API administrativa de produtores | `contracts/openapi/v1.yaml`, `docs/api-guide.md` | `ProducerAdminApiIT`; contrato+geração Angular; 401/403/CSRF/400/404/409/201/200/405 | local `feat/c18-producer-persistence` | CAT-Q01–03 aplicadas; só admin; API sem DELETE, com flag de demonstração; trabalho pendente de publicação |
| C20 — UI administrativa de produtores | `specs/SPEC-catalog.md` §6–7, decisão D65 | `admin.component.spec.ts` (carregamento/criação); Playwright `admin-producers.spec.ts` (criar, editar, desativar, recarregar) | local `feat/c20-producer-admin-ui` | Conteúdo claramente demonstrativo; sem botão de exclusão; perfil inativo e referências persistem; publicação pendente |
| C21 — persistência de produtos e SKUs | `specs/SPEC-catalog.md` §§3–7, D17–D18/D27/D54/D57/D65 | `ProductCategoryInvariantTest`, `ProductSkuPackagingTest`, `ProductRepositoryIT`; PostgreSQL 18.6/Testcontainers + Flyway V14 + Hibernate validate | PR #33 aberta, branch `feat/c21-catalog-product-persistence` | Dois SKUs de conteúdos distintos ligados ao produto; código duplicado, validade/conteúdo e dimensão inválidos rejeitados; desativação preserva IDs, ligações e atributos SKU; snapshots independentes pertencem a pedidos; commit `7e009a0` validado localmente; retry CI `35639675604` 7/7 verde |
| C22 — API de administração e consulta de produto | `specs/SPEC-catalog.md` §§7–10, OpenAPI `Product*` | `ProductAdminApiIT`; PostgreSQL 18.6/Testcontainers + Flyway V15; verificação de contrato e cobertura de rotas | local `feat/c22-product-api-contracts`, empilhada sobre PR #33 | Criar/editar/listar/consultar; sem DELETE; gate backend completo (15 unitários/15 integrações) e rodada final C22 (4/4 integrações) verdes; contrato/geração TS aprovados; FOOD/CRAFT, rollback, autorização/CSRF, 404/409 e filtros públicos verificados; commit/PR pendentes |

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
