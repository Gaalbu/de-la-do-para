# Registro de progresso — De Lá do Pará

Documento operacional previsto no plano mestre (§1 “Registro para continuidade”).
Atualizar ao final de cada sessão, somente após evidência verificada.

## Sessão 2026-09-20 — bootstrap C00a/C00b

| Campo | Conteúdo |
|---|---|
| Base | branch `main`, sem commits iniciais (repo vazio); plano `docs/PLANO-MESTRE.md` versão consolidação 20/09/2026, sha256 `c67d245e4d7758b4a7931fd16b5e7049651fc859bfb5aaf47cf2f4362f88efc6` |
| Tarefa | C00a (pré-clone) e C00b (preservar plano no clone) concluídas e enviadas (`7437919`) |
| Mudanças | Repo público `Gaalbu/de-la-do-para` criado vazio via `gh`; clone em `/home/gaalbu/codigos/de-la-do-para`; plano copiado para `docs/PLANO-MESTRE.md` com hash idêntico ao original; criados `tasks/progress.md`, `.gitignore`, `.env.example`, `README.md`. Nenhum código LAPES copiado; nenhum histórico reutilizado |
| Verificação | `git clone https://github.com/Gaalbu/de-la-do-para.git` (repo vazio, exit 0); `git remote -v` confere origin `Gaalbu/de-la-do-para`; destino `de-la-do-para` não existia antes (verificado com `ls`); `sha256sum` do plano original e da cópia idênticos; `git status --short` limpo antes do commit; push `main` confirmado e repo verificado PUBLIC via `gh repo view` |
| Remoto | https://github.com/Gaalbu/de-la-do-para — `main@7437919` enviado; resultado local separado de CI (sem CI ainda) |
| Próximo passo | C01 — `docs(scope)`: transcrever escopo aprovado e mapa de capacidades (D01–D64) sem inferência; depois C02 convenções SDD/ADR |
| Perguntas | Namespace Java proposto `br.com.deladopara` confirmado pelo usuário; licença do código público e destino de releases seguem pendentes para a etapa de preparação de repo/release (não bloqueiam C01) |

## Sessão 2026-09-20 — C01 escopo (revisado e merged)

| Campo | Conteúdo |
|---|---|
| Base | branch `docs/c01-scope` a partir de `main@7437919`; merge `b9e70f8` em `main` |
| Tarefa | C01 — `docs(scope)`: transcrever escopo aprovado e mapa de capacidades — concluída e merged via PR #1 |
| Mudanças | Criados `docs/scope.md` (escopo, 12 módulos/dependências, ordem, metas) e `docs/decisions.md` (D01–D64, A01–A10, Q01 e pendências). Transcrição fiel, sem inferências novas |
| Verificação | Revisão automatizada vs plano mestre: decisões 64/64 (100%), propostas 10/10 (100%), módulos 12/12 (100%), figuras-chave 26/26 (100%); spot-check D54/D22 confere; `git diff --check` limpo; sem segredos; PR #1 com estado MERGEABLE/CLEAN; revisão humana do usuário + autorização de merge obtidas |
| Remoto | PR #1 merged (`b9e70f8`); `main` atualizada via fast-forward; sem CI ainda |
| Próximo passo | C02 — `docs(sdd)`: convenções de spec/ADR e matriz de rastreio |
| Perguntas | Nenhuma nova; licença/destino de releases seguem pendentes para etapa própria |

## Sessão 2026-09-20 — C02/C03/C04 (PRs #2–#4 abertos; respostas do usuário)

| Campo | Conteúdo |
|---|---|
| Base | `main@fab0fd3`; branches `docs/c02-sdd` (`3b0824f`, PR #2), `docs/c03-design` (`e966f58`, PR #3), `docs/c04-integrations` (`9e61fe5`, PR #4) |
| Tarefa | C02 (convenções SDD), C03 (design, Q01 segue aberta), C04 parcial (docs revalidados; spike pendente) — PRs abertos, merges com o usuário |
| Mudanças | `docs/contributing.md`, `spec-template.md`, `adr-template.md`, `traceability.md`, `spec-example.md`; `docs/design/{brief,storyboard,assets}.md`; `docs/integrations/{asaas,melhor-envio,homologation}.md`. Nenhuma integração alegada como comprovada |
| Verificação | `git diff --check` limpo nos 3 branches; sem segredos; links internos/externos conferidos (fontes OFL, 4 docs de provedores, Commons); contrastes WCAG medidos; fidelidade C01 100% |
| Remoto | PRs #2, #3, #4 abertos; `main` só com C00a/C00b/C01; sem CI ainda |
| Próximo passo | C05 — `docs(architecture)`: ADRs (usa template de C02 já conhecido; sem bloqueio) |
| Perguntas | Respondidas: túnel **Cloudflare quick tunnel aprovado** (atualizar `homologation.md` no merge do PR #4); credenciais Asaas via `.env` local com ajuda posterior; Melhor Envio sandbox simplificado confirmado. Abertas: revisão dos PRs #2–#4; Q01 criativa (C03); licença/destino de releases |

## Sessão 2026-09-20 — revisão PRs #2–#5, marco G0 fechado

| Campo | Conteúdo |
|---|---|
| Base | `main` de `8584626` até `298f4d9`; 5 PRs revisados um a um com o usuário |
| Tarefa | Revisão e merge de C02 (PR #2, `c74ba24`), C03 (PR #3, `87a05bd`), C04 (PR #4, `1911c83`), C05 (PR #5, `298f4d9`) — sem merge automático; cada merge autorizado |
| Mudanças | Q01 resolvida: paleta/fontes/fotos aprovadas + regra anti-genérica; principal em loop ≤30 s sem legendas (D49 emendada); túnel Cloudflare registrado no PR #4. PR #4 exigiu sync via merge de `main` (`2c287a6`, sem rebase) após "base modified" |
| Verificação | `git diff --check` limpo nas emendas; sem segredos; PRs CLEAN/MERGEABLE antes de cada merge; `gh pr list` confirma 5/5 MERGED; `git status` limpo |
| Remoto | `main@298f4d9` enviada; sem CI ainda (C13) |
| Próximo passo | C06 — `chore(backend)`: bootstrap Spring Boot (namespace `br.com.deladopara`); depois C07 Angular, C08 Compose. Credenciais sandbox via `.env` com ajuda posterior, no spike |
| Perguntas | Abertas: curadoria das 7 fotos (C03); spike sandbox (contas do usuário); licença/destino de releases |

## Sessão 2026-09-20 — C06 bootstrap backend (PR #6 merged)

| Campo | Conteúdo |
|---|---|
| Base | branch `chore/c06-backend` (`40be312`) a partir de `main@f9bd6ac`; merge `b7354fe` |
| Tarefa | C06 — `chore(backend)`: bootstrap Spring Boot — concluída, validada e merged |
| Mudanças | `backend/pom.xml` (parent Boot 4.1.1, Java 25, `br.com.deladopara`), `mvnw`+wrapper Maven 3.9.16, `DeLaDoParaApplication`, `application.yml` mínimo, `contextLoads`. Sem negócio fictício; `target/` ignorado |
| Verificação | Boot 4.1.1 confirmado no Central antes de fixar; `./mvnw -B verify` BUILD SUCCESS (Tests 1, 0 falhas); boot em 1,58 s exit 0; validação independente: sentido/aceite 4/4 OK, sem segredos reais, superfície mínima |
| Remoto | PR #6 merged (`b7354fe`); sem CI ainda |
| Próximo passo | C07 — `chore(frontend)`: bootstrap Angular standalone + SSR |
| Perguntas | Nenhuma nova |

## Matriz de versões fixada (C06)

- JDK 25.0.4 GraalVM CE; Spring Boot 4.1.1; Spring Framework 7.0.9 (spring-core do BOM); Maven wrapper 3.9.16 (scripts 3.3.4); Node v22.23.2 / npm 12.0.2 a fixar em C07.

## Sessão 2026-09-20 — C07 bootstrap frontend (PR #7 merged, auto-merge autorizado)

| Campo | Conteúdo |
|---|---|
| Base | branch `chore/c07-frontend` (`b549d68`); merge `13664fc` |
| Tarefa | C07 — `chore(frontend)`: bootstrap Angular standalone + SSR — concluída, validada e merged (merge próprio autorizado pelo usuário quando aprovado) |
| Mudanças | Workspace `frontend/` via CLI 22.1.8 (skip-git, npm): Angular 22.1.x, TS ~6.0.2, RxJS ~7.8, Vitest, SSR+prerender, routing, lockfile. Ajuste manual: `strict:true` + `strictTemplates:true` (scaffold não trazia os masters) |
| Verificação | `ng test` 2 passed; `ng build` SSR + `/` prerenderizada (21 KB HTML real c/ `ng-server-context`); sem zone.js; latin-ext confirmado p/ Fraunces+Inter (css2 API). Obs: 1 core-dump transitório no 1º build, verde nas 2 compilações seguintes; SSR rejeita Host externo por SSRF protection padrão |
| Remoto | PR #7 merged (`13664fc`); sem CI ainda |
| Próximo passo | C08 — `chore(dev)`: Compose local (PostgreSQL, Kafka KRaft, Mailpit, simuladores) + `.env` + guia |
| Perguntas | Nenhuma nova |

## Matriz de versões fixada (C06–C07)

- JDK 25.0.4 GraalVM CE; Spring Boot 4.1.1; Spring Framework 7.0.9; Maven wrapper 3.9.16; Node v22.23.2; Angular 22.1.x; TypeScript ~6.0.2; RxJS ~7.8; Vitest 4; packageManager npm@12.0.2 (lockfile v-compatível, installs OK).

## Sessão 2026-09-20 — C08 infra local (PR #8 merged, auto-merge autorizado)

| Campo | Conteúdo |
|---|---|
| Base | branch `chore/c08-local-infra` (`a861f81`); merge `150630a` |
| Tarefa | C08 — `chore(dev)`: Compose local + proxy + guia — concluída, validada e merged |
| Mudanças | `compose.yml` (postgres 18.6-bookworm, kafka 4.3.1 KRaft, mailpit v1.31.2, wiremock 3.13.2-alpine; portas próprias; healthchecks); `frontend/proxy.conf.json` (/api→8080); `.env.example` com portas; `docs/local-guide.md`; mappings vazios. `.env` local criado e ignorado |
| Verificação | `config` OK; 4/4 `healthy`; PG write/read; Kafka produce→consume; Mailpit/WireMock via host; `down` preserva volumes; `up` restaura dados; sondas removidas. Falhas reais corrigidas: PGDATA no parent (guia oficial 18+), `KAFKA_CONTROLLER_LISTENER_NAMES`, healthcheck com path absoluto |
| Remoto | PR #8 merged (`150630a`); sem CI ainda |
| Próximo passo | C09 — `build(backend)`: formatter, Testcontainers, arquitetura e análise Java |
| Perguntas | Nenhuma nova |

## Sessão 2026-09-20 — C09 gates backend (PR #9 merged, auto-merge autorizado)

| Campo | Conteúdo |
|---|---|
| Base | branch `build/c09-backend-gates` (`e69b73d`); merge `ff5097c` |
| Tarefa | C09 — `build(backend)`: formatter, Testcontainers, arquitetura e análise — concluída, validada e merged |
| Mudanças | Spotless 3.10.2+palantir 2.98 + checkstyle 3.6 (regras mínimas) + failsafe explícito; TC BOM 2.0.5 (módulos renomeados `testcontainers-*`); `ArchitectureRulesTest` (3 regras); `InfrastructureIT` (PG 18.6 + Kafka 4.3.1); deps kafka-clients + postgresql driver |
| Verificação | `verify` verde: unit 4/4, IT 2/2 (PG+Kafka reais), spotless+checkstyle passam; violação `@Autowired` temporária → gate vermelho com mensagem da regra → removida (só o arquivo experimental) → verde de novo |
| Remoto | PR #9 merged (`ff5097c`); sem CI ainda |
| Próximo passo | C10 — `build(frontend)`: lint, formato, runner, Playwright |
| Perguntas | Nenhuma nova |

## Sessão 2026-09-20 — C10 gates frontend (PR #10 merged, auto-merge autorizado)

| Campo | Conteúdo |
|---|---|
| Base | branch `build/c10-frontend-gates` (`1a73d3f`); merge `707a26b` |
| Tarefa | C10 — `build(frontend)`: lint, formato, runner, Playwright — concluída, validada e merged |
| Mudanças | ESLint flat (angular 22.5 + tseslint 8.70 + a11y, `no-explicit-any` error); prettier `format`/`format:check`; `test:ci`; Playwright 1.63 + `e2e:local` (webServer :4200); `zoneless.spec.ts`; scripts F/E; ignores p/ test-results |
| Verificação | F: lint 0 erros, format OK, `test:ci` 4 passed (inclui zoneless), build SSR OK. E: smoke 1 passed em 16,5 s. Correções: templateRecommended exigiu `files`+`extends`; scaffold formatado |
| Remoto | PR #10 merged (`707a26b`); sem CI ainda |
| Próximo passo | C11 — `build(contracts)`: OpenAPI/AsyncAPI + cliente gerado |
| Perguntas | Nenhuma nova |

## Sessão 2026-09-20 — C11 contratos (PR #11 merged, auto-merge autorizado)

| Campo | Conteúdo |
|---|---|
| Base | branch `build/c11-contracts` (`b52619b`); merge `d7b1afd` |
| Tarefa | C11 — `build(contracts)`: schemas + cliente gerado — concluída, validada e merged |
| Mudanças | `contracts/openapi/v1.yaml` seed (status, ProblemDetail, security); envelope JSON Schema + exemplos; redocly 2.53, hey-api 0.99, ajv 8.20; `contracts:check` encadeado; gerado em `src/generated` (ignorado) |
| Verificação | Cadeia verde (lint válido + 3 warnings documentados, exemplos ok/ok, 4 arquivos gerados, tsc limpo); spec inválida falha com 2 erros (fixture removida); gerado isento de lint/format. Decisões: hey-api (openapi-typescript exige TS5); warnings mantidos; AsyncAPI em C45 |
| Remoto | PR #11 merged (`d7b1afd`); sem CI ainda |
| Próximo passo | C11a — `docs(api)`: referência interativa + exemplos executáveis |
| Perguntas | Nenhuma nova |

## Sessão 2026-09-20 — C11a referência e exemplos (PR #12 merged, auto-merge autorizado)

| Campo | Conteúdo |
|---|---|
| Base | branch `docs/c11a-api-reference` (`d257320`); merge `0cac71d` |
| Tarefa | C11a — `docs(api)`: referência + exemplos — concluída, validada e merged |
| Mudanças | `docs/api-guide.md`; `status.http` (executável + futuro comentado); stub WireMock `/api/v1/status`; checklist API-E-TESTES no spec-template §8 |
| Verificação | Stub 200 com seed; Redoc 40 KB HTTP 200 com título; futuros marcados não-funcionais. Incidentes: bind WireMock estagnado (recreate, sem dados em jogo); redocly 2.x sem `preview-docs` (guia usa `build-docs`+http.server, verificado) |
| Remoto | PR #12 merged (`0cac71d`); sem CI ainda |
| Próximo passo | C11b — `test(contracts)`: detectar rotas sem contrato e schema drift |
| Perguntas | Nenhuma nova |

## Sessão 2026-09-20 — C11b harness de contrato (PR #13 merged, merge autorizado)

| Campo | Conteúdo |
|---|---|
| Base | branch `build/c11b-contract-harness` (`edca8cc`) retomado com trabalho não-commitado da sessão anterior; merge `37f86ec` |
| Tarefa | C11b — `test(contracts)`: rotas sem contrato + schema drift — concluída, validada e merged |
| Mudanças | `RouteContractCoverageTest` (implementação→contrato; allowlist só p/ infra, `/error` única exceção); `redocly.yaml` (`extends: recommended` + `no-invalid-schema-examples: error`); `--config` explícito no `contracts:lint`; seção Gates em `docs/api-guide.md`; `spring-boot-starter-web` escopo test (web de produção chega em C15+) |
| Verificação | `verify` verde: unit 2/2, arch 3/3, IT 2/2 (PG+Kafka reais), spotless+checkstyle OK; `contracts:check` verde (lint válido + 3 warnings de C11, events, generate, tsc); fixtures divergentes falham pelo motivo esperado (allowlist vazia → `rotas sem contrato: [/error ...]`; exemplo inválido → erro redocly exit 1); `diff --check` limpo; sem segredos. Incidentes: field injection violou `noFieldInjection` (→ construtor); spotless aplicado; config sem `extends` zerava warnings de C11 e sem `--config` a regra não carregava de `frontend/` |
| Remoto | PR #13 merged (`37f86ec`); sem CI ainda |
| Próximo passo | C12 — `feat(observability)`: correlação HTTP, logs estruturados, health/readiness, teste de redaction |
| Perguntas | Nenhuma nova |

## Matriz de versões fixada (C06–C09)

- JDK 25.0.4; Boot 4.1.1; Spring 7.0.9; Maven 3.9.16; Node v22.23.2; Angular 22.1.x; TS ~6.0.2; RxJS ~7.8; Vitest 4; Spotless 3.10.2/palantir 2.98; checkstyle-plugin 3.6; ArchUnit 1.5; TC BOM 2.0.5; PG 18.6; Kafka 4.3.1; Mailpit v1.31.2; WireMock 3.13.2.

## Ambiente registrado (C00a)

- Git 2.43.0; Docker Compose v5.5.1 (Docker 29.8.1); JDK 25.0.4 GraalVM CE; Node v22.23.2; npm 12.0.2 (versão a revalidar no bootstrap frontend C07, matriz Angular 22).
- RAM ~15 Gi disponível; portas locais em uso observadas: 5432 (PostgreSQL), 6379 (Redis), 8080 — o profile `local` do Compose usará portas/volumes próprios do projeto (C08).
- `/home/gaalbu/codigos` não é repo git (pasta de projetos); nenhum reset/clean/force executado; pasta `tasks/` pré-existente em `/home/gaalbu/codigos` não foi tocada.
