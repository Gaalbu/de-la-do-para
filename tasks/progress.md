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

## Ambiente registrado (C00a)

- Git 2.43.0; Docker Compose v5.5.1 (Docker 29.8.1); JDK 25.0.4 GraalVM CE; Node v22.23.2; npm 12.0.2 (versão a revalidar no bootstrap frontend C07, matriz Angular 22).
- RAM ~15 Gi disponível; portas locais em uso observadas: 5432 (PostgreSQL), 6379 (Redis), 8080 — o profile `local` do Compose usará portas/volumes próprios do projeto (C08).
- `/home/gaalbu/codigos` não é repo git (pasta de projetos); nenhum reset/clean/force executado; pasta `tasks/` pré-existente em `/home/gaalbu/codigos` não foi tocada.
