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

## Sessão 2026-09-20 — C12 e CI inicial C13 (PR #14)

| Campo | Conteúdo |
|---|---|
| Base | `main@281ee74`; branch `feat/c12-observability`, sem mudanças locais prévias |
| Tarefa | C12 implementada e verificada localmente; C13 inicial implementada, execução remota acompanhada no PR #14. C13a–C13c permanecem pendentes |
| Mudanças | Correlação HTTP/JSON, Actuator com probes, OpenAPI e testes; harness suporta múltiplos mapeamentos; exclusão de HTML gerado do lint; workflow CI + entrada local de gates; README atualizado |
| Commits | `07b469a` observabilidade; `679c3bc` correção lint; `6c7556d` CI. C12 inclui contrato, documentação e testes no mesmo commit (9 arquivos, acoplamento necessário para gate coerente) |
| Verificação | Teste de correlação vermelho por classe ausente → 4 testes verdes; health vermelho 404 → 3 testes HTTP verdes; `verify` Temurin 25.0.4: 12 unitários/HTTP/arquitetura + 2 IT reais PostgreSQL/Kafka, formatter/checkstyle verdes. F: lint/formato, 4 testes e build SSR verdes; E: 1 smoke Playwright verde. Contratos verdes, warnings Redocly registrados no output. JAR real: JSON/correlação conferidos, query/cookie/token de teste ausentes; RSS 232.596 KiB após uma sonda, sem carga. Shell inválido retorna 2; YAML/pinagem/permissões conferidos; diff check limpo |
| Incidentes | Baseline GraalVM CE sofreu SIGSEGV em `libjvmcicompiler.so` (SHA implCompress0); ArchUnit e suíte completa passam no Temurin já instalado, selecionado por comando, sem mudar Java global. Lint preexistente falhava no HTML Redoc gerado (ICU); ignorado apenas artefato. Aislop/actionlint indisponíveis; revisão manual e gates existentes executados |
| Remoto | https://github.com/Gaalbu/de-la-do-para/pull/14 — MERGED `a631ef8` (run `35547099486` success 4/4). https://github.com/Gaalbu/de-la-do-para/pull/15 — MERGED `d2fb069` (run `35547722865` docs success). Verificado `statusCheckRollup` verde antes de cada merge |
| Próximo passo | Confirmar CI do HEAD e revisão/merge conforme autorização; depois C13a documentação, C13b segurança, C13c política/agenda/proteção. G1 externo continua dependente de C04 real, não de mocks |
| Perguntas | Nenhuma nova nesta entrega. Credenciais/spike C04, fotos restantes e licença/destino de releases continuam na etapa correspondente. Preservada decisão posterior: principal em loop de até 30 s sem legendas |

## Sessão 2026-09-20 — C13a gate documental (PR #15)

| Campo | Conteúdo |
|---|---|
| Base | `main@a631ef8`; branch `ci/c13a-docs` |
| Tarefa | C13a — `ci(docs): enforce documentation and API example checks` — concluída, verificada localmente e na CI, merged |
| Mudanças | `frontend/scripts/check-docs.mjs` (links internos Markdown), `docs:check` em `frontend/package.json`, `scripts/verify.sh` com caso `docs` (check-docs + contracts:check), job `docs` no `ci.yml` + `docs` no `quality-gate`, `docs/ci.md` atualizada (C13 → C13+C13a, docs:check reproduzível) |
| Commits | `5c099a6` ci(docs) |
| Verificação | `npm run docs:check` OK 24 arquivos; fixture quebrado `nao-existe-xyz.md` falha com ERRO documentado → restaurado verde; `scripts/verify.sh docs` OK (6 warnings Redocly documentados + generate + tsc); `lint` 0, `format:check` OK (após `prettier --write`), `test:ci` 4 passed, `build` SSR OK; `spotless:check` OK, `diff --check` limpo. CI `35547722865` success: backend/frontend/contracts/docs/quality-gate 5/5 |
| Incidentes | `format:check` falhou no novo mjs até `prettier --write`; eslint ok (gerado ignorado) |
| Remoto | https://github.com/Gaalbu/de-la-do-para/pull/15 — MERGED `d2fb069` |
| Próximo passo | C13b `ci(security): enforce scoped permissions and dependency checks` (pinagem, scans, dependabot) — depois C13c `ci(quality): require healthy PRs and scheduled checks` (agregador docs-only, proteção de main) |
| Perguntas | Nenhuma nova |

## Sessão 2026-09-21 — C13b gate de segurança (PR #16 merged)

| Campo | Conteúdo |
|---|---|
| Base | `main@d2fb069`; branch `ci/c13b-security` (`b84a01d`) |
| Tarefa | C13b — `ci(security): enforce scoped permissions and dependency checks` — concluída, verificada e merged |
| Mudanças | `permissions: contents: read` no workflow e job `security`; actions pinadas por SHA (`checkout@11d5960a`, `setup-java@cf277c60`, `setup-node@49933ea`, `upload-artifact@ea165f8d`); `scripts/check-secrets.sh` + `.secret-allowlist.txt` + `scripts/verify.sh security` (secrets + `npm audit --omit=dev --audit-level=high` 0 high; completo 4 high dev-only triados); `.github/dependabot.yml` semanal (npm/maven/actions); `docs/ci.md` com triagem |
| Verificação | `check-secrets.sh` OK, fixture `AKIA...` falha como esperado; `verify.sh security` OK; `npm run lint` 0, `format:check` OK, `diff --check` limpo; runtime audit 0 high, completo 4 high dev-only documentados (`js-yaml` via `@hey-api` GHSA-52cp/5p4m/2883); CI `355481...` success backend/frontend/contracts/docs/security/quality-gate |
| Remoto | https://github.com/Gaalbu/de-la-do-para/pull/16 — MERGED `d0ce796` |
| Próximo passo | C13c `ci(quality): require healthy PRs and scheduled checks` |
| Perguntas | Nenhuma nova |

## Sessão 2026-09-21 — C13c gate de qualidade e proteção (PR #25 merged)

| Campo | Conteúdo |
|---|---|
| Base | `main@d0ce796`; branch `ci/c13c-quality` (`071315e`, `68faf7e`) |
| Tarefa | C13c — `ci(quality): require healthy PRs and scheduled checks` — concluída, verificada e merged |
| Mudanças | `scripts/check-commits.sh` (Conventional Commits `feat|fix|test|docs|refactor|build|ci|chore`, merge ignorado); job `commit-policy` (`fetch-depth:0` + check); `schedule: 17 9 * * 1` semanal; `quality-gate` exige 6 checks (backend/frontend/contracts/docs/security/commit-policy); `verify.sh` casos `commits`/`all`; `docs/ci.md` § agenda/gates/proteção; branch protection via API `strict:true`, contexts 7, `enforce_admins:false` |
| Verificação | `check-commits.sh origin/main HEAD` OK, fixture ruim reprova; `verify.sh docs/security/commits` OK; `lint` 0, `format:check` OK, `diff --check` limpo; CI `35548287393` success 7/7 (backend/frontend/contracts/docs/security/commit-policy/quality-gate) |
| Remoto | https://github.com/Gaalbu/de-la-do-para/pull/25 — MERGED `2db8139` (commits `071315e`, `68faf7e`) |
| Próximo passo | G1 fechado (CI/docs/security/commit-policy verificados); C14 `docs(identity): specify sessions and optional customer accounts` — spec SDD antes de C15 |
| Perguntas | Nenhuma nova; spike C04 sandbox e curadoria de fotos permanecem para etapa própria |

## G1 — verificação

- CI com 7 jobs obrigatórios, proteção de `main` verificada via `gh api repos/Gaalbu/de-la-do-para/branches/main/protection`; docs-only não bypassa gates; sandbox C04 continua pendente opt-in (não comprovado por mocks).

## Sessão 2026-09-21 — C14 spec identity (PR #26 merged)

| Campo | Conteúdo |
|---|---|
| Base | `main@4f592b0`; branch `docs/c14-identity` (`4b82a3f`) |
| Tarefa | C14 — `docs(identity): specify sessions and optional customer accounts` — concluída, verificada e merged após revisão humana |
| Mudanças | `specs/SPEC-identity.md` (9 seções: objetivo, comandos, estrutura B/identity + migrations V10-V12, convenções BCrypt12/cookie/CSRF, estratégia 12 testes, limites OAuth/2FA fora de v1, regras papéis/invariantes/transições R01-R08, contratos I-01..I-08, critérios IDN-001..013) |
| Verificação | `docs:check` OK 24 arquivos; `verify.sh docs` OK (6 warnings Redocly pré-existentes); `verify.sh security` OK (0 high runtime); `diff --check` limpo; CI `35548577157` success 7/7 (backend/frontend/contracts/docs/security/commit-policy/quality-gate) |
| Remoto | https://github.com/Gaalbu/de-la-do-para/pull/26 — MERGED `2edd714` |
| Próximo passo | C15 `feat(identity): authenticate administrators with protected sessions` — B/identity, migrations, T/identity; dividir persistência/API se >5 arquivos |
| Perguntas | Nenhuma nova; OAuth/2FA, remetente de e-mail e durações exatas confirmadas como propostas a validar em C15 |

## Sessão 2026-09-21 — C15 identity backend (PR #27 merged)

| Campo | Conteúdo |
|---|---|
| Base | `main@2edd714`; branch `feat/c15-identity` (`b8342b9`) |
| Tarefa | C15 — `feat(identity): authenticate administrators with protected sessions` — concluída, verificada e merged |
| Mudanças | B/identity: `Account`/`VerificationToken`, `AccountRepository`/`VerificationTokenRepository`, migrations V10-V12 (accounts, verification_tokens, SPRING_SESSION), `ClockConfig`, BCrypt12, cookie `DLSESSION` (HttpOnly/Secure/SameSite=Lax), CSRF `CookieCsrfTokenRepository`, `SecurityConfig` (permitAll health/status/csrf/accounts/sessions + `/error`+`/actuator/**`, `changeSessionId`, 401/403 ProblemDetail), `AccountController` (201/409), `SessionController` (200/401/204), `CsrfController`, `IdentityExceptionHandler`, `AdminSeeder` (senha aleatória logada), `IdentityProperties`/`IdentityConfig`/`IdentityUserDetailsService`; `pom` `spring-boot-starter-flyway`, `application.yml` datasource/jpa/flyway/session/mail, profile test sem URL fixa, `PostgresTestContainer` + `HealthEndpointTest` `@Import`; `contracts/openapi/v1.yaml` I-01..I-08 + schemas; `docs/traceability.md` atualizado |
| Verificação | `verify` Temurin 25.0.4: `mvnw verify` BUILD SUCCESS (Tests 13, spotless 0, checkstyle 0, Flyway V10-V12 aplicadas, PG real via Testcontainers); `contracts:check` valid 10 warnings + generate+tsc OK; `docs:check` 24 OK; `security` OK; fix: starter flyway ausente, mail health 503, env 401 via `/error` permitAll. CI `35552149863` success 7/7 (backend/frontend/contracts/docs/security/commit-policy/quality-gate) |
| Remoto | https://github.com/Gaalbu/de-la-do-para/pull/27 — MERGED `be93aab` |
| Próximo passo | C16 `feat(identity-ui): add accessible login and admin navigation` — depende C10+C15, F/identity + guarda de rota |
| Perguntas | Nenhuma nova; verificação/recuperação single-use e rate-limit (IDN-003..012) ficam para C76/C77 |

## Sessão 2026-09-21 — C16 identity-ui (PR #28 merged)

| Campo | Conteúdo |
|---|---|
| Base | `main@be93aab`; branch `feat/c16-identity-ui` (`549221d`) |
| Tarefa | C16 — `feat(identity-ui): add accessible login and admin navigation` — concluída, verificada e merged |
| Mudanças | `core/interceptors/csrf.interceptor.ts` (XSRF-TOKEN→X-XSRF-TOKEN), `IdentityService` (signals, login/logout/fetchCurrent, withCredentials), `LoginComponent` (form_validado, preserva valores, redirect por papel, a11y alert), `AdminComponent`+`adminGuard` (fetchCurrent se necessário), `HomeComponent`, `App` header com navegação por sessão + `isPlatformBrowser` para SSR, `app.config` HttpClient withFetch+csrf, rotas lazy, `app.routes.server` (prerender login, server ''/admin/**), `app.spec` atualizado, `angular.json` allowedHosts `[localhost:4200]`, `playwright.config` com `node ../dist/...` + PORT env e fallback `python`→`serve`→`node` |
| Verificação | `lint` 0, `format:check` OK, `test:ci` 4 passed, `build` SSR OK (prerender 1, lazy chunks), `contracts:check` OK, `docs:check` 24 OK; `mvnw verify` OK; fix: `ng serve` vite timeout (300s→30s com `serve`/`python`→`node` SSR, allowedHosts 400, `''` redirect loop); CI `3555...` success 7/7 (backend/frontend/contracts/docs/security/commit-policy/quality-gate) |
| Remoto | https://github.com/Gaalbu/de-la-do-para/pull/28 — MERGED `0de79a9` |
| Próximo passo | C17 `docs(catalog): specify provenance products and packaging` — depende C01/C02/C03, spec catálogo |
| Perguntas | Nenhuma nova |

## Sessão 2026-09-21 — C17 spec de catálogo e procedência (aprovada)

| Campo | Conteúdo |
|---|---|
| Base | `main@c5df90411d9eaba7f8c4e68c25db58488ea8014d`; estado inicial continha apenas alterações C17 e `.angular/` não rastreado, preservado |
| Tarefa | C17 — `docs(catalog): specify provenance products and packaging` — spec revisada e aprovada pelo usuário |
| Mudanças | `specs/SPEC-catalog.md`: limites, produtor/produto/SKU, regras alimentos/artesanato, dimensões, embalagem, imagem/licença, contratos propostos, CAT-001..009 e perguntas. CAT-Q01 respondida: só admin gere produtores na v1, sem conta própria. Atualizados `docs/decisions.md`, `docs/traceability.md`. |
| Verificação | `npm run docs:check` OK (24 Markdown); `npm run contracts:check` exit 0 (10 warnings Redocly preexistentes; exemplos, geração e TypeScript OK); `git diff --check` OK. |
| Remoto | PR #30 aberto: https://github.com/Gaalbu/de-la-do-para/pull/30, `docs/c17-catalog@4c424dc`. CI run `35631089081` success: backend, frontend, contracts, docs, security, commit-policy e quality-gate (7/7). PR mergeable; não merged. |
| Próximo passo | C18 — persistência de produtores/procedência (PostgreSQL, migration e teste de integração). CAT-Q02/03 permanecem para C19. Curadoria final de assets continua pendente em C03, sem adicionar imagens não aprovadas. |
| Perguntas | Nenhuma nova para C18. |

## Sessão 2026-09-21 — C18 persistência de produtores

| Campo | Conteúdo |
|---|---|
| Base | `docs/c17-catalog@91ed77b`; branch `feat/c18-producer-persistence`; árvore inicial preservava `.angular/` não rastreado |
| Tarefa | C18a/C18b — `feat(catalog): persist producers and provenance` — implementada e verificada localmente |
| Mudanças | `Producer` com UUID estável, slug normalizado para minúsculas, nome de exibição, rótulo amplo de origem, descrição e timestamps; migration `V13__catalog_producers.sql` com checks de slug/formato/campos, PK UUID e índice único case-insensitive; `ProducerRepository` expõe save/read/findBySlug/existsBySlug sem API de exclusão; testes cobrem persistência, atualizações, identidade/timestamp estáveis e duplicação de slug. CAT-Q01 respeitada: nenhum produtor tem conta própria. |
| Verificação | RED inicial confirmou ausência da tabela; `./mvnw -Dit.test=ProducerPersistenceIT,ProducerRepositoryIT verify` BUILD SUCCESS: unitários 12/12 e integração 3/3, PostgreSQL 18.6 via Testcontainers, Flyway V13 aplicada, Hibernate `ddl-auto=validate`, Spotless e Checkstyle OK; `git diff --check` OK; `npx aislop scan --changes --json` 100/100, 0 findings. |
| Remoto | PR #30 (C17) permanece OPEN/MERGEABLE, CI no head `91ed77b` SUCCESS 7/7. C18/C19 locais nesta branch, ainda não publicadas. |
| Próximo passo | C20 — interface administrativa para consultar/criar/editar produtores, conforme contrato C19 e decisões D65. |
| Perguntas | CAT-Q01 confirmada anteriormente; CAT-Q02/Q03 aprovadas pelo usuário em 2026-09-21 e registradas como D65. Sem contas para produtores nem exclusão física. |

## Sessão 2026-09-21 — C19 API administrativa de produtores

| Campo | Conteúdo |
|---|---|
| Base | branch local `feat/c18-producer-persistence`; C18 implementada na árvore atual; `.angular/` preexistente não rastreado preservado |
| Decisões | Usuário aprovou CAT-Q02/Q03: sem remoção física de produtor referenciado, com desativação; apenas localidade ampla e texto editorial fictício, identificados como demonstração, sem coordenadas/endereço/alegações verificáveis. D65 registrado em `docs/decisions.md` e `docs/PLANO-MESTRE.md`. |
| Mudanças | C18: `Producer`/repositório/migration V13 com `active`; constraints e índice único. C19: serviço e rotas administrativas GET/POST/lista/consulta/PATCH, paginação limitada, DTOs de demonstração, conflito de slug, validação/Problem Details correlacionado; sem DELETE. OpenAPI atualizado e clientes Angular regenerados; guia da API e rastreabilidade atualizados. SecurityConfig agora inclui `correlationId` RFC UUID nas respostas 401/403. |
| Verificação | `./mvnw -Dit.test=ProducerPersistenceIT,ProducerRepositoryIT,ProducerAdminApiIT verify` BUILD SUCCESS: unitários 12/12, integração C18+C19 7/7; PostgreSQL 18.6/Testcontainers, Flyway V13, Hibernate validate, Spotless e Checkstyle OK. `contracts:check` exit 0 (spec válida, eventos, geração Angular e TypeScript; 10 avisos Redocly já existentes); `docs:check` 24 Markdown OK; `verify.sh security` zero segredos e zero vulnerabilidades runtime (4 high preexistentes dev-only em js-yaml); `git diff --check` OK; aislop 100/100, zero findings. |
| Remoto | PR #30 de C17 OPEN/MERGEABLE, CI 7/7 no head `91ed77b`; implementação C18/C19 ainda não publicada. |
| Próximo passo | C20 UI de gestão administrativa, coberta por teste browser real e reload da lista/detalhes. |
| Perguntas | Nenhuma nova; produtores não têm conta própria na v1. |

## Sessão 2026-09-21 — C20 interface administrativa de produtores

| Campo | Conteúdo |
|---|---|
| Base | `feat/c18-producer-persistence` (C18/C19); branch `feat/c20-producer-admin-ui`; `.angular/` preexistente não rastreado preservado |
| Decisões | D65 aplicada: somente admin; desativar em vez de apagar; conteúdo fictício/amplo com identificação de demonstração. SSR de `/admin` renderiza o shell sem cookies; autorização efetiva ocorre no guard do browser e, obrigatoriamente, na API. |
| Mudanças | Página administrativa lista/pagina produtores ativos e inativos, cria, edita e desativa/restaura; formulário acessível com required/pattern/maxlength, avisos contra endereço/coordenadas/alegações e estados de loading/erro/sucesso. Sem ação de exclusão. Corrigido guard SSR: sessão baseada em cookie só é consultada no browser; API segue responsável por autorização. |
| Verificação | `./scripts/verify.sh frontend` passou: lint, format, 6 testes Angular, build SSR, Playwright 2/2. `verify.sh docs`, `contracts` (10 avisos Redocly), `security` (0 vulnerabilidades runtime; 4 high dev-only preexistentes em js-yaml) passaram; `git diff --check` OK; aislop 100/100 sem findings. |
| Remoto | Commit `02bf308` enviado; PR #32 aberto/mergeable sobre `feat/c18-producer-persistence`; CI `35637344901` 7/7 verde. PR #31 (C18/C19) aberto/mergeable e CI 7/7 verde. |
| Próximo passo | C21 — persistência de produtos/SKUs na branch empilhada sobre C20. |
| Perguntas | Nenhuma. |

## Sessão 2026-09-21 — C21 persistência de produtos e SKUs

| Campo | Conteúdo |
|---|---|
| Base | Branch `feat/c21-catalog-product-persistence`, baseada em C20/C19/C18; `.angular/` preexistente não rastreado preservado |
| Decisões | D65: produtores somente por admin, sem conta própria; produtores referenciados ficam sem exclusão física, podem ser desativados; exibição usa somente localidade ampla e texto editorial fictício identificados como demonstração. C21 protege produto/SKUs referenciados por FK restritiva; snapshots independentes pertencem à futura capacidade de pedidos. Fixture real/sintética do catálogo permanece em C26. |
| Mudanças | Entidades `Product` e `ProductSku`, repositórios sem operação de exclusão física, migration V14 com unicidade case-insensitive, invariantes de categoria/conteúdo/validade e dimensões/peso. SKU representa unidade vendida, com variantes distintas por conteúdo/embalagem. Desativar produto preserva identidades e relações. Spec esclarece limites de snapshots e fixture; rastreabilidade e plano atualizados. |
| Verificação | `./mvnw -Dit.test=ProducerPersistenceIT,ProducerRepositoryIT,ProducerAdminApiIT,ProductRepositoryIT verify` passou: 15 testes unitários, 10 integração, PostgreSQL 18.6/Testcontainers, Flyway V14, Spotless e Checkstyle sem violações. `npm run docs:check --prefix frontend` passou (24 Markdown); `git diff --check` OK. |
| Remoto | C20 PR #32 aberto/mergeable, CI `35637344901` 7/7 verde. C21 commit `7e009a0` publicado em PR #33, empilhada sobre C20; retry do CI `35639675604` concluiu 7/7 verde após 502 transitório do Maven Central. |
| Próximo passo | C22 — API de administração e consulta pública sobre C21; sem merge automático. |
| Perguntas | Nenhuma. |

## Sessão 2026-09-21 — C22 API de produtos (verificada localmente)

| Campo | Conteúdo |
|---|---|
| Base | Branch `feat/c22-product-api-contracts`, empilhada sobre C21/C20; `.angular/` preexistente não rastreado preservado |
| Decisões | Produtos ativos só aparecem publicamente quando produtor e ao menos um SKU também estão ativos. PATCH integral; SKU omitido desativado logicamente. Nenhuma rota DELETE. Escritas só admin com CSRF. |
| Mudanças | Teste HTTP RED confirmou rota ausente; OpenAPI agora define rotas/admin/public e schemas. Implementados `ProductService`, controllers, DTOs, mapeamento Problem Details, autorização pública explícita GET e migration V15 para estado ativo do SKU. Documento API/spec atualizados. |
| Verificação | `./scripts/verify.sh backend` passou: 15 unitários, 15 integrações incluindo PostgreSQL 18.6/Testcontainers, Flyway V15 e harness Kafka, Spotless e Checkstyle. Rodada final `-Dit.test=ProductAdminApiIT verify` passou 15 unitários + 4 integrações, cobrindo FOOD/CRAFT (campos alimentares nulos), rollback, 401/403/CSRF/404/409, inatividade de produtor/produto/SKUs e ausência de DELETE. `contracts:check` passou com geração TypeScript, `tsc` e eventos (10 avisos Redocly preexistentes); `docs:check` (24 Markdown) e `git diff --check` passaram. |
| Remoto | C22 `fca692a` publicado na PR #34 sobre #33: https://github.com/Gaalbu/de-la-do-para/pull/34, MERGEABLE/CLEAN; CI `35642638538` success 7/7 (backend, frontend, contracts, docs, security, commit-policy, quality-gate). |
| Próximo passo | C23 — interface administrativa de produtos, branch empilhada sobre C22; sem merge automático. |
| Perguntas | Nenhuma. |

## Sessão 2026-09-21 — C23 interface administrativa de produtos (verificada localmente)

| Campo | Conteúdo |
|---|---|
| Base | `feat/c22-product-api-contracts@fca692a`; branch `feat/c23-product-admin-ui`; `.angular/` não rastreado preexistente preservado |
| Tarefa | C23 — `feat(catalog-ui): manage products and packaging details` — commit `c540d3f`, PR #35 aberta; correção de CI em andamento |
| Mudanças | Nova rota protegida `/admin/products` com SSR; lista paginada, seleção de produtores ativos, formulário de alimentos/artesanato, variantes editáveis, campos alimentares condicionais, dimensões/peso/fragilidade, erros recuperáveis, edição e confirmação ao desativar produto. UI ligada à API tipada de C22; sem upload de imagem (C24) |
| Verificação | `./scripts/verify.sh frontend` passou localmente: lint, Prettier, 10 testes Angular, build SSR e Playwright 3/3. O primeiro CI limpo falhou porque `frontend/src/generated/` é ignorado e o job não gerava os tipos OpenAPI. Após corrigir `scripts/verify.sh frontend` para rodar `contracts:generate`, o mesmo gate passou em arquivo limpo extraído do commit, com node_modules compartilhado; geração, lint, 10 testes, build SSR e Playwright 3/3. API no E2E é simulada; endpoints PostgreSQL/HTTP foram cobertos em C22. `npx aislop scan --changes --json`: 100/100, sem achados. |
| Remoto | C22 PR #34 aberta, MERGEABLE/CLEAN, CI `35642638538` 7/7 verde. C23 PR #35 aberta sobre #34; run `35649968314` falhou em `frontend` e `quality-gate`; correção local pronta para publicar e reexecutar CI. |
| Próximo passo | Publicar correção do gate, confirmar CI 7/7 de C23 e seguir implementação C24 na branch empilhada; nenhum merge automático |
| Perguntas | Nenhuma nova |

## Sessão 2026-09-21 — C24 imagem principal de catálogo (em implementação)

| Campo | Conteúdo |
|---|---|
| Base | `feat/c24-media`, empilhada em C23; `.angular/` preexistente preservado |
| Decisões | Uma imagem principal opcional por produto; JPEG/PNG; máximo 5 MiB. Limites técnicos internos: 12 MP e 6000 px por lado. |
| Mudanças | Spec/ADR 0006; processador raster que confere bytes e remove metadados; armazenamento local UUID em `APP_MEDIA_DIRECTORY`; domínio e migration V16; rotas multipart admin, remoção e entrega pública condicionada a produto/produtor/SKU ativos; OpenAPI/DTO; formulário administrativo com prévia, licenciamento, confirmação, upload e remoção; guia local/API atualizado. |
| Verificação | Backend `verify`: 31 testes unitários e 18 integrações passaram com PostgreSQL 18.6, migrations até V16, Spotless e Checkstyle. Foi necessário `-DargLine=-Xint` após SIGSEGV no compilador C1 de Temurin 25.0.4. `scripts/verify.sh frontend`: geração OpenAPI, lint, formato, 11 testes Angular, build SSR e Playwright 3/3. `docs:check` validou 25 Markdown; `contracts:check` passou com 10 avisos Redocly preexistentes; security detectou zero segredos e audit runtime zero vulnerabilidades; `aislop scan --changes --json`: 100/100, zero achados; `git diff --check` limpo. |
| Remoto | C24 implementação `1d5b2ac`, HEAD de documentação `9491996`, PR #36: https://github.com/Gaalbu/de-la-do-para/pull/36, base `feat/c23-product-admin-ui`. Estado atual reconsultado: PR aberta/MERGEABLE, CI `35656338034` 7/7 verde. C23 PR #35 aberta/MERGEABLE, CI `35650869375` 7/7 verde. Nenhum merge automático. |
| Próximos passos | C24 entregue para revisão remota; acompanhar as PRs empilhadas sem merge automático. |

## Sessão 2026-09-21 — C24a políticas logísticas e comerciais (proposta para revisão)

| Campo | Conteúdo |
|---|---|
| Base | `feat/c24-media@9491996`; nova branch `docs/c24a-logistics-decisions`, empilhada sem integrar PRs abertas |
| Tarefa | C24a — consolidar regras já aprovadas e encaminhar lacunas às etapas designadas; D66/D67 aprovadas, revisão final da spec pendente |
| Mudanças | Criadas `specs/SPEC-logistics.md` e `specs/SPEC-pricing.md`; atualizados C24a em `docs/PLANO-MESTRE.md`, `docs/decisions.md` e rastreio. D21–D31, D33–D37 e D54–D60 resumidas sem reabrir respostas; D66 (guarda por 3 dias úteis + análise manual) e D67 (fixture sintética dos limites D54, clock fixo) aprovadas e registradas. O escopo de C24a foi precisado para deixar design/metas/catálogo em suas specs próprias. Recomendações futuras seguem identificadas como propostas |
| Verificação | `npm run docs:check --prefix frontend` aprovado: 25 arquivos Markdown, nenhum link quebrado; `git diff --check` aprovado; conferência manual dos pontos citados com D21–D31, D33–D37 e D54–D60. Fontes oficiais federal/estadual e atos municipais de Belém consultados; a cobertura municipal anual permanece a confirmar antes de montar calendário completo. `aislop scan --changes --json`: 100/100, zero achados |
| Remoto | Ainda sem commit/PR |
| Próximo passo | Registrar a resposta sobre despacho parcial e apresentar a pendência seguinte; C25 pode usar fixture D67, mas segue após conclusão/revisão de C24a e conferência do calendário municipal |
| Perguntas | Nenhuma nova; escolhas de imagem e tamanho já aprovadas |

## Sessão 2026-09-21 — C25 inventário (spec em revisão)

| Campo | Conteúdo |
|---|---|
| Base | `docs/c24a-logistics-decisions@9491996`; alterações locais ainda não commitadas |
| Tarefa | C25 — especificar lotes, saldos, reservas e invariantes; spec preparada, não concluída |
| Mudanças | Criado `specs/SPEC-inventory.md`: saldo físico/reservado/livre, reserva de 15 minutos, pagamento tardio D13, elegibilidade `arrivalDate`/validade, fixture D67, alocação all-or-nothing, ledger/auditoria, handoff por pacote e critérios INV-001–008. FEFO, bloqueio com reserva ativa, calendário anual e validade em retirada tardia ficaram explicitamente abertos |
| Verificação | Ainda executar após esta edição: `npm run docs:check --prefix frontend`, `git diff --check` e `aislop scan --changes --json`. Nenhum código ou migration foi criado |
| Remoto | Commit `a819ffd` publicado na PR #37: https://github.com/Gaalbu/de-la-do-para/pull/37, base `feat/c24-media`; PR aberta/MERGEABLE; CI inicial `35658857246` 7/7 verde. Nenhum merge automático |
| Próximo passo | Acompanhar CI do commit de progresso; depois perguntar a regra de despacho parcial e obter revisão da spec antes de marcar C25 concluída |

## Sessão 2026-09-21 — C29 preço e cupons (spec em revisão)

| Campo | Conteúdo |
|---|---|
| Base | `docs/c24a-logistics-decisions@a6e5915`; PR #37 aberta, sem merge |
| Tarefa | C29 — especificar cálculo monetário, descontos e elegibilidade de cupons; proposta documental, não concluída |
| Mudanças | Ampliada `specs/SPEC-pricing.md` com centavos inteiros, ordem de subtotal/frete/desconto, arredondamento half-up proposto, limite de desconto, mínimo/validade/e-mail verificado, snapshots e casos de fronteira. Combinações e contador global permanecem decisões abertas |
| Verificação | Executar `npm run docs:check --prefix frontend`, `git diff --check` e `aislop scan --changes --json` após a edição; sem código ou migration |
| Remoto | PR #37 cobre a spec-base; nova alteração ainda local |
| Próximo passo | Validar documentação; pedir revisão das regras propostas antes de C30/C31 |

## Sessão 2026-09-21 — plano executável C30/C31

| Campo | Conteúdo |
|---|---|
| Base | `docs/c24a-logistics-decisions@7259c35`; PR #37 aberta, 7/7 verde |
| Tarefa | Preparar ordem de implementação sem aprovar silenciosamente o contador global |
| Mudanças | Criado `tasks/plan.md` com fatias C30 (totais canônicos + BT) e C31 (reserva atômica + BI), dependências, gates e bloqueios; rastreio atualizado |
| Verificação | Executar `npm run docs:check --prefix frontend`, `git diff --check` e `aislop` após edição; sem código funcional |
| Próximo passo | Revisão das specs C25/C29 e decisão do ciclo global de cupons; depois iniciar C30 |

## Sessão 2026-09-21 — C30 totais canônicos

| Campo | Conteúdo |
|---|---|
| Base | `docs/c24a-logistics-decisions@7259c35`; implementação em `feat/c30-pricing-totals` |
| Tarefa | C30 — calcular total canônico em centavos BRL |
| Mudanças | Criados `Money`/`Currency` (BRL), `PurchaseLine`, `PurchaseTotalRequest`, `CouponDiscount`, `PurchaseTotal` e `PurchaseTotalCalculator`; desconto percentual half-up, mínimo, desconto fixo limitado ao subtotal, frete e total com aritmética exata |
| Verificação | RED por compilação sem tipos; depois teste focado verde e `./mvnw -q -DargLine=-Xint verify` verde; `aislop scan --staged --json`: 100/100, zero achados |
| Remoto | Commits `a7a0551`, `5813f3f`; PR #38: https://github.com/Gaalbu/de-la-do-para/pull/38; CI final 7/7 verde, aberta e mergeable; nenhum merge automático |
| Próximo passo | Definir contador global de cupons e iniciar C31; expor cálculo por API só quando o contrato de checkout estiver definido |

## Sessão 2026-09-21 — C26 invariantes de lote

| Campo | Conteúdo |
|---|---|
| Base | `feat/c30-pricing-totals@6409c4f`; implementação em `feat/c26-inventory-lot-invariants` |
| Tarefa | C26 — núcleo independente de saldos e elegibilidade de lote |
| Mudanças | Criados `InventoryLot`, `InventoryAvailability`, `InventoryMovement`, `InventoryLedger`, `InventoryReservation` e `ReservationIntent`: saldos, margem D67, bloqueio, ledger idempotente, expiração de 15 minutos e validação multi-SKU |
| Verificação | Testes focados e `./mvnw -q -DargLine=-Xint verify` verdes; `aislop` 100/100, zero achados |
| Remoto | Commits até `e81ea6e`; PR #39: https://github.com/Gaalbu/de-la-do-para/pull/39; rodada atual do CI em andamento, PR aberta e mergeable |
| Limites | FEFO, lotes bloqueados com alocação ativa, persistência, reserva e API continuam fora desta fatia até revisão das regras abertas |

## Sessão 2026-09-22 — C33 especificação da vitrine pública

| Campo | Conteúdo |
|---|---|
| Base | `main@7ee7090`; branch `docs/c33-storefront-spec`; `.angular/` não rastreado preexistente preservado |
| Tarefa | C33 — `docs(storefront): specify public discovery and rendering` — concluída e merged |
| Mudanças | `specs/SPEC-storefront.md` define rotas públicas, query strings compartilháveis, filtros/ordenação/paginação, elegibilidade pública de produto/SKU/produtor, saldo livre, SSR sem sessão, estados de erro e critérios de teclado/mobile/reduced-motion conforme C03. |
| Verificação | `npm run docs:check --prefix frontend` OK (25 Markdown), `git diff --check` OK, `npx aislop scan --changes --json` 100/100; PR #40 CI 7/7 verde, merged em `7ee7090`. |
| Remoto | PR #40: https://github.com/Gaalbu/de-la-do-para/pull/40 — MERGED; PRs #30–#39 também merged em ordem. |
| Próximo passo | C34 requer uma fonte de preço por SKU no módulo `pricing`; C30 é apenas cálculo puro e o catálogo explicitamente não deve copiar preço. Não implementar consulta incompleta nem inventar fixture sem decisão/contrato. |

## Sessão 2026-09-22 — C34 primeira fatia de preços correntes

| Campo | Conteúdo |
|---|---|
| Base | `main@7ee7090`; branch `feat/c34-storefront-queries`; `.angular/` não rastreado preexistente preservado |
| Tarefa | C34 — fonte pricing-owned de preço corrente por SKU — primeira fatia concluída e merged |
| Mudanças | `SkuPrice` validado em BRL/centavos positivos; tabela `pricing_sku_prices` em V19 com FK para SKU e restrições; `SkuPriceEntity`/repository; `SkuPriceService` carrega preços em lote; testes unitários e `SkuPriceRepositoryIT`. |
| Verificação | PR #42 CI 7/7 verde: backend real com PostgreSQL 18.6/Testcontainers e Flyway V19, frontend, contracts, docs, security, commit-policy e quality-gate; merge commit `b77bd3c`. |
| Remoto | PR #42: https://github.com/Gaalbu/de-la-do-para/pull/42 — MERGED. |
| Próximo passo | Implementar `StorefrontQueries`: filtro/ordenação/paginação e composição de produto, preço corrente e saldo livre; não marcar C34 concluída antes de teste de consulta vazia, limites, filtros inválidos e reserva ativa. |

## Sessão 2026-09-22 — C34 consulta composta e C35 catálogo público

| Campo | Conteúdo |
|---|---|
| Base | `main@923c882`; branch `feat/next-catalog-work`; `.angular/` não rastreado preexistente preservado |
| Tarefa | C34 concluída no PR #45; iniciar C35 com catálogo público navegável |
| Mudanças | C34 expõe `GET /api/v1/products` com filtros combináveis, ordenação/paginação, preço corrente e saldo livre por SKU; `StorefrontAvailability` e `StorefrontPricing` mantêm fronteiras acíclicas. C35 adiciona a página Angular da vitrine na rota `/`, filtros e ordenação persistidos em query params, estados de carregamento/vazio/erro, cards editoriais responsivos e paginação limitada. |
| Verificação | Backend remoto do PR #45 verde em todos os checks; frontend local `format:check`, `lint`, build SSR e `storefront.component.spec.ts` (3 testes) verdes. |
| Remoto | C34 merged em `923c882`; C35 PR #46 merged em `c28a120`. |
| Próximo passo | Concluir C36 com detalhe público/procedência, preço e disponibilidade por SKU; depois seguir para C37. |

## Sessão 2026-09-22 — C36 detalhe público e procedência

| Campo | Conteúdo |
|---|---|
| Base | `main@c28a120`; branch `feat/storefront-product-detail` |
| Tarefa | C36 — iniciar detalhe público do produto e procedência |
| Mudanças | Backend amplia o DTO público com preço corrente e saldo livre por SKU; contrato OpenAPI atualizado. Frontend adiciona `/products/:slug`, descrição, origem/produtor, imagem, preço e estado explícito de SKU indisponível ou produto não disponível. |
| Verificação | `ProductAdminApiIT` verde; `contracts:check` verde; frontend `format:check`, `lint`, teste da vitrine (3 testes) e build SSR verdes. |
| Remoto | C35 merged em `c28a120`; C36 ainda local, sem commit/PR. |
| Próximo passo | Adicionar teste específico do detalhe público, rodar gate final/aislop disponível, commitar e abrir PR C36. |

## Sessão 2026-09-22 — C36 concluído e C37 produtor público

| Campo | Conteúdo |
|---|---|
| Base | `main@9a9c122`; branch `feat/storefront-producer-page` |
| Tarefa | C36 merged; iniciar C37 com página pública do produtor |
| Mudanças | C36 PR #47 adicionou detalhe público, procedência, preço/saldo por SKU e estados de indisponibilidade. C37 adiciona `GET /api/v1/producers/{slug}` com produtor ativo e produtos filtrados pela consulta storefront, além da rota Angular `/producers/:slug` e navegação a partir dos cards. |
| Verificação | PR #47 CI 7/7 verde e merged em `9a9c122`; backend compile/Spotless, `contracts:check`, frontend format/lint, 4 testes de vitrine e build SSR verdes nesta branch. |
| Remoto | C36 merged; C37 ainda local, sem commit/PR. |
| Próximo passo | Adicionar teste específico de produtor, rodar gate final/aislop disponível, commitar e abrir PR C37. |

## Sessão 2026-09-22 — C37 concluído e C38 carrinho convidado

| Campo | Conteúdo |
|---|---|
| Base | `main@67c641e`; branch `feat/storefront-next-step` |
| Tarefa | C37 merged; iniciar C38 — especificar carrinho convidado e snapshots |
| Mudanças | C37 PR #48 entrega produtor público e seus produtos. C38 cria `specs/SPEC-cart.md` com sessão convidada, proprietário, versão otimista, conflitos 409, combinação explícita no login, snapshot imutável e limites de responsabilidade entre cart/catalog/inventory/pricing/checkout. |
| Verificação | PR #48 CI 7/7 verde e merged em `67c641e`; C38 pendente de docs:check, diff check e revisão da spec. |
| Remoto | C37 merged; C38 ainda local, sem commit/PR. |
| Próximo passo | Rodar gates documentais, commitar e abrir PR C38; depois C39 só após o contrato ser revisado. |

## Sessão 2026-09-22 — C38 concluído e C39 persistência de carrinho

| Campo | Conteúdo |
|---|---|
| Base | `main@8505626`; branch `feat/cart-persistence` |
| Tarefa | C38 merged; iniciar C39 — persistir carrinho convidado/versionado |
| Mudanças | C38 PR #49 especifica ownership, conflitos e snapshots. C39 adiciona migration V20 para `carts`/`cart_items`, restrições de proprietário/status/quantidade, entidades JPA, repository e agregado de domínio com versão otimista e transição para checkout. API de mutações permanece reservada ao C39a. |
| Verificação | PR #49 CI 7/7 verde e merged em `8505626`; `CartTest`, `InfrastructureIT`, Spotless e compilação backend verdes. |
| Remoto | C38 merged; C39 ainda local, sem commit/PR. |
| Próximo passo | Adicionar integração PostgreSQL do repository e converter o agregado em serviço transacional antes de abrir PR C39. |
## Sessão 2026-09-22 — C39a API de carrinho convidado

- C39 foi mergeado no PR #50 (`f126e94`).
- Implementada API pública de carrinho: `GET /api/v1/cart`, substituição de itens, remoção por SKU e limpeza.
- Sessão HTTP é persistida somente como SHA-256; mutações exigem CSRF e `expectedVersion`.
- Duplicidade de SKU, SKU inativo/inexistente, versão obsoleta e concorrência JPA retornam erros contratuais.
- Teste `GuestCartApiIT` cobre reload, mutação, conflito e remoção; próximo passo é C40 (UI persistente) após publicar o PR.
## Sessão 2026-09-22 — C40 UI de carrinho persistente

- C39a foi mergeado no PR #51 (`22f8ae7`) após correção do contrato de rotas e isolamento do handler de validação.
- Implementada UI `/cart`, contador no cabeçalho e integração de adicionar/editar/remover/limpar com `expectedVersion`.
- O SKU público agora expõe seu UUID para a UI; contrato OpenAPI e geração TypeScript foram atualizados.
- Validação frontend: 16 testes, build, lint, format e contracts:check verdes; backend de catálogo/carrinho validado localmente.
- Próximo passo: C41, regras de cotação/expedição.
## Sessão 2026-09-22 — C41 especificação de shipping

- C40 foi mergeado no PR #52 (`a193c4b`), com carrinho persistente na UI.
- Criada `specs/SPEC-shipping.md` com fingerprint/validade, modalidades,
  pacotes, preparo, retirada, expedição parcial e cancelamento.
- Regras D17/D18 e limites C41a–C43 permanecem separados, sem inventar
  adapter ou capacidade física.
- Próximo passo: C41a, composição determinística de pacotes.
## Sessão 2026-09-22 — C41a composição de pacotes

- C41 foi mergeado no PR #53 (`e95e2b8`).
- Implementado `PackageComposer` determinístico: separação FOOD/CRAFT, frágeis isolados, proteção D58/D60, caixas P/M/G D59 e alocação única de unidades.
- Testes cobrem separação, fragilidade, menor caixa e unidade incompatível.
- Próximo passo: C42, adapter de cotação sandbox.

## Sessão 2026-09-22 — C42 adapter de cotação sandbox

- Implementados `FreightQuoteAdapter`, `ShippingQuoteRequest`, `CarrierQuote` e `ShippingAdapterProperties`.
- `MelhorEnvioSandboxAdapter` interpreta somente payload sandbox fornecido pelo chamador; não faz chamadas externas em testes comuns.
- Valida cobertura exata de todos os pacotes, custo, prazo, validade e campos obrigatórios; falhas são normalizadas sem expor credenciais.
- Testes focados e checkstyle/Spotless passaram localmente.
- Próximo passo: commitar, abrir PR e aguardar os gates remotos antes de considerar C42 concluído.

## Sessão 2026-09-22 — C43 persistência de cotação

- C42 foi mergeado no PR #55 (`66a433c`), com todos os gates verdes.
- Criada migration V21 e entidade/repository de `shipping_quotes`, vinculando a cotação ao `snapshot_id`/versão e preservando fingerprint, destino, pacotes, custo, prazos e validade.
- Criado agregado `ShippingQuote` com invariantes de identidade, valores e expiração; teste cobre validade inválida.
- Próximo passo: commitar, abrir PR e validar a migration com o backend completo.

## Sessão 2026-09-22 — C43 serviço de persistência da cotação

- Adicionado `ShippingQuoteService`, que persiste somente cotações ainda válidas,
  serializa a cobertura dos pacotes e conserva snapshot/version/fingerprint.
- Teste unitário confirma a gravação com identidade do snapshot e sequência dos pacotes.
- Próximo passo: publicar a extensão e depois implementar a origem de cotação do checkout.

## Sessão 2026-09-22 — C44 contrato de checkout

- O serviço de persistência do C43 foi mergeado no PR #57 (`542ce2c`), com CI completo.
- Criada `specs/SPEC-checkout.md` para fixar endereço, entrega/retirada, resposta
  `AVAILABLE`/`UNAVAILABLE`, invalidação e erros antes da UI.
- Próximo passo: implementar a origem/API de delivery-options e então a tela C44.
- Implementado `DeliveryOptionsService`: normaliza CEP, consulta apenas cotações persistidas do snapshot/versão ainda válidas e reconstrói a cobertura de pacotes; sem endpoint público até existir validação de ownership do snapshot.

## Sessão 2026-09-22 — C44 snapshot de checkout

- Implementado o primeiro slice executável do C44: `POST /api/v1/checkout/snapshots`
  congela o carrinho convidado da sessão em migration V22 e retorna
  `snapshotId`/`snapshotVersion`; a sessão é armazenada somente como hash.
- Rota, schema OpenAPI e autorização pública foram atualizados; o snapshot
  rejeita carrinho inexistente ou vazio e permanece imutável.
- Verificação: testes focados `CheckoutSnapshotServiceTest,CheckoutControllerTest`,
  `./mvnw -q -DargLine=-Xint verify` com PostgreSQL/Testcontainers e Flyway V22,
  `npm run contracts:lint` (válido, 11 avisos Redocly preexistentes),
  `git diff --check` e `npx aislop scan --changes --json` (100/100, zero achados).
  A execução completa de `contracts:check` encontrou o SIGSEGV intermitente do
  TypeScript durante `tsc`, após a geração de tipos; não é tratado como gate verde.
- Commit local: `864db49` (`feat(checkout): create guest checkout snapshots`),
  ainda não publicado.
- Próximo passo: expor `GET /api/v1/checkout/{snapshotId}/delivery-options`,
  validando ownership pela sessão, versão, CEP e validade das cotações antes de
  iniciar a UI de endereço/seleção.

## Sessão 2026-09-22 — C44 opções de entrega por snapshot (local, pronto para publicação)

- Adicionado `GET /api/v1/checkout/{snapshotId}/delivery-options` com `snapshotVersion` e `postalCode`.
- `CheckoutSnapshotService` valida ownership pelo hash da sessão e versão exata antes de delegar a `DeliveryOptionsService`, que mantém apenas cotações persistidas, do destino normalizado e não expiradas.
- Erros contratuais: snapshot ausente/pertencente a outra sessão (404), versão obsoleta (409) e CEP inválido (400), com `ProblemDetail` e correlação.
- OpenAPI atualizado com `DeliveryOptions`/`ShippingQuote`; testes unitários do serviço e controller adicionados.
- Verificação: `./mvnw -q -DargLine=-Xint verify` terminou sem falhas/erros nos relatórios Surefire/Failsafe; `npm run contracts:check` exit 0 (11 avisos Redocly preexistentes); `./mvnw -q spotless:apply` exit 0; `git diff --check` OK; `npx aislop scan --changes --json` 100/100, zero achados. Uma execução paralela anterior deixou `hs_err_pid27759.log` e SIGSEGV do GraalVM; o gate com `-Xint` foi usado e os relatórios finais ficaram verdes.
- Remoto: ainda sem commit/PR desta extensão; alterações permanecem locais sobre `feat/checkout-address-contract`.
- Próximo passo: revisar/stagear somente os cinco arquivos da fatia, commitar e abrir PR C44; depois validar o CI remoto antes de iniciar a UI de endereço/seleção.

## Sessão 2026-09-22 — C44 opções de entrega publicadas

- Commit local `bf58cb6` foi reconciliado com a reescrita remota da branch por merge explícito `cc926ec`, sem force-push, e publicado em `feat/checkout-address-contract`.
- PR #60 aberta: https://github.com/Gaalbu/de-la-do-para/pull/60.
- CI remoto `35766151122` verde em 6/6: backend, frontend, contracts, docs, security e commit-policy. Avisos de Node.js 20 nas actions foram reportados pelo runner, sem falha de gate.
- PR permanece aberta/mergeable, sem merge automático.
- Próximo passo: iniciar a UI C44 de endereço e seleção de modalidade em branch empilhada, preservando a decisão de merge humano do PR #60.

## Sessão 2026-09-22 — C44 UI inicial de endereço e cotação (local)

- Branch `feat/checkout-address-ui`, empilhada sobre `feat/checkout-address-contract` após CI remoto verde.
- Criados `CheckoutService` e página `/checkout`: cria snapshot, envia o CEP ao endpoint, exibe carregamento/erro/nenhuma opção/opções válidas e não calcula frete localmente.
- Carrinho ganhou link explícito para continuar à entrega; nenhum pedido, cobrança, reserva ou endereço inferido foi implementado.
- Teste do serviço cobre a ordem snapshot → consulta server-owned, incluindo `snapshotVersion` e `postalCode`.
- Verificação local: frontend `test:ci` 8 arquivos/17 testes verdes, `format:check` OK, `lint` OK e `build` SSR OK; `npx aislop scan --changes --json` 100/100, zero achados; `git diff --check` OK.
- Remoto: ainda não publicado; UI permanece local até revisão/validação visual e E2E da jornada real.
- Próximo passo: adicionar teste de componente/jornada para os estados da tela e executar a jornada em navegador real antes de abrir PR empilhada.

### Evidência adicional da jornada C44

- Navegador real em `http://127.0.0.1:4200/checkout`: página, formulário rotulado `CEP`, botão `Consultar` desabilitado sem valor e navegação para `/cart` foram observados no accessibility tree.
- Com CEP sintético `66053-000`, a UI exibiu o estado recuperável “Não foi possível consultar este CEP” porque não havia API em `127.0.0.1:8080`; portanto não há alegação de cotação integrada nesta sessão.
- `docker compose ps` e `GET /api/v1/status` confirmaram que o backend/stack local não estavam em execução. A alteração incidental de `frontend/angular.json` (analytics=false criada pelo CLI) foi revertida.
- Próximo passo permanece: iniciar stack autorizada ou usar backend local para jornada integrada, adicionar teste de componente e só então publicar a PR da UI.

### Evidência runtime integrada C44

- Compose local subiu com PostgreSQL, Kafka, Mailpit e WireMock saudáveis; backend iniciou com `POSTGRES_PASSWORD=dlp-local-dev` e `-Dspring-boot.run.jvmArguments=-Xint`.
- `GET /actuator/health` respondeu 200 e Flyway confirmou schema v22; o primeiro boot sem `-Xint` repetiu o SIGSEGV do GraalVM já conhecido.
- Navegador real com proxy `/api`: `/cart` respondeu 200 com carrinho vazio; a tela `/checkout` enviou CEP sintético `66053-000`, exibiu carregamento e retornou ao estado de erro recuperável quando não havia snapshot de carrinho elegível.
- Não foi criado dado administrativo apenas para fabricar sucesso: o caminho de opções disponíveis continua sem evidência integrada até existir fixture de catálogo/carrinho aprovada.
- Serviços locais foram encerrados com `docker compose --profile local down`, sem remover volumes; próximo passo é adicionar teste de componente/jornada e publicar a UI em PR empilhada.

## Sessão 2026-09-22 — C44 UI testada e pronta para PR

- Adicionado `checkout.component.spec.ts`: botão de consulta permanece desabilitado sem CEP e a tela renderiza somente opção de entrega retornada pelo servidor.
- Verificação: `npm run test:ci` 9 arquivos/19 testes verdes; `npm run lint` e `npm run format:check` OK; `npx aislop scan --changes --json` 100/100, zero achados.
- A UI continua em `feat/checkout-address-ui`, empilhada sobre PR #60; caminho de sucesso permanece coberto por mocks de contrato no teste, enquanto a jornada integrada real confirmou apenas o erro correto para carrinho vazio.
- Próximo passo: commitar, publicar e abrir PR empilhada; não fazer merge automático.

## Sessão 2026-09-22 — C44 UI publicada

- Commit `385b503` publicado em `feat/checkout-address-ui`; PR #61 aberta contra `feat/checkout-address-contract`: https://github.com/Gaalbu/de-la-do-para/pull/61.
- CI remoto `35767275847` verde em 7/7: backend, frontend, contracts, docs, security, commit-policy e quality-gate.
- PR #60 segue aberta/mergeable como base do contrato; nenhum merge automático foi feito.
- Próximo passo: revisão humana dos PRs #60/#61 e, após integração autorizada, avançar para seleção persistente de modalidade/continuação do checkout conforme C44.

## Sessão 2026-09-22 — C44 erro recuperável coberto

- Acrescentado teste de componente para `404` ao criar snapshot; a tela mantém o formulário e expõe `role="alert"` para nova tentativa.
- Verificação: frontend `test:ci` 9 arquivos/20 testes verdes, lint e formatação OK; `aislop` 100/100 e `git diff --check` OK.
- PR #61 receberá esta extensão; CI será reexecutado no novo head. PRs #60/#61 continuam abertos, sem merge automático.

## Sessão 2026-09-22 — C44 seleção server-owned de cotação

- Implementada a seleção de uma cotação via `POST /api/v1/checkout/{snapshotId}/delivery-selection`, validando ownership da sessão, `snapshotVersion`, `inputFingerprint` e expiração; o fluxo não cria pedido, cobrança ou reserva.
- A tela `/checkout` agora envia a seleção server-owned e mostra a modalidade selecionada; testes cobrem contrato HTTP, serviço e componente.
- Verificação local: backend focado com `-DargLine=-Xint` passou; frontend `test:ci` 9 arquivos/22 testes, lint e formatação passaram; contrato OpenAPI válido com 11 warnings preexistentes do lint; `git diff --check` passou.
- Limitação conhecida: `./mvnw -q -DargLine=-Xint verify` ainda termina por SIGSEGV do GraalVM durante a verificação da JVM; o relatório foi preservado em `backend/hs_err_pid54502.log`.
- Próximo passo: rodar `aislop`, commitar/publicar a extensão no PR #61 e aguardar o CI remoto; nenhum merge automático.

### Evidência adicional da seleção C44

- O serviço de checkout ganhou teste explícito de seleção somente para snapshot pertencente à sessão e versão atual; o teste verifica a delegação server-owned ao serviço de cotações.
- O CI remoto `35768644650` terminou verde em 7/7 no commit `5c9eb3a`, incluindo backend real com PostgreSQL/Kafka e frontend com browser smoke.
- A nova cobertura local passou em nova execução com `-DargLine=-Xint`; houve uma falha intermitente adicional de SIGSEGV do GraalVM durante uma execução anterior, preservada como `backend/hs_err_pid56795.log`.

## Sessão 2026-09-22 — C44 comparação explícita de pacotes

- A UI passou a exibir a cobertura de pacotes retornada pelo servidor junto da modalidade, prazo total e custo; nenhum pacote é inferido no frontend.
- Verificação: frontend `test:ci` 9 arquivos/22 testes, lint e formatação OK.
- Próximo passo: publicar essa extensão e continuar a recuperação de seleção inválida/expirada; retirada permanece pendente da origem de opções correspondente no servidor.
