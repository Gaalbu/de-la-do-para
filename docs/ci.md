# CI — C13, C13a, C13b e C13c

A pipeline `.github/workflows/ci.yml` executa em PRs, push em `main`, disparo
manual e semanalmente (`schedule: 17 9 * * 1` seg 09:17 UTC). Jobs `backend`,
`frontend`, `contracts`, `docs`, `security` e `commit-policy` usam os mesmos
comandos do ambiente local. `quality-gate` exige sucesso de todos, inclusive
em mudança só documental; falha, cancelamento e skip não passam.

```bash
npm ci --prefix frontend
# Docker ativo para PostgreSQL/Kafka dos testes de integração:
scripts/verify.sh backend
scripts/verify.sh docs
scripts/verify.sh security
scripts/verify.sh commits
scripts/verify.sh contracts
scripts/verify.sh frontend
# ou todos, sequencialmente:
scripts/verify.sh
```

`docs` verifica links internos de Markdown (`frontend/scripts/check-docs.mjs`)
e reexecuta `contracts:check` (lint, exemplos vs schema e cliente gerado).
Um link quebrado ou exemplo fora do schema reprova o job; a verificação é
reproduzível localmente e na CI sem acesso à rede, e cada endpoint futuro
herda a checagem pelo mesmo gate.

`security` verifica segredos (`scripts/check-secrets.sh`) e dependências
(`npm audit --omit=dev --audit-level=high` para runtime; audit completo
informativo). Achado controlado com fixture `AKIA...` falha como esperado;
repositório limpo passa. PRs externos rodam com `permissions: contents: read`
e sem acesso a segredos do repositório.

`commit-policy` verifica Conventional Commits (`scripts/check-commits.sh`:
`feat|fix|test|docs|refactor|build|ci|chore` com escopo opcional, 72 chars);
merge commits são ignorados, mensagem fora do padrão reprova. Fixture
`mensagem ruim sem tipo` falha como esperado.

O backend usa Temurin 25.0.4 na CI. Na máquina de desenvolvimento, o teste de
arquitetura encontrou SIGSEGV no compilador nativo da GraalVM CE 25.2.4,
`libjvmcicompiler.so`, durante o baseline. O mesmo teste e a suíte completa
passaram no Temurin 25.0.4 já instalado. Nenhum teste foi desativado e o Java
padrão da máquina não foi alterado. Para selecionar por comando:

```bash
JAVA_HOME=/home/gaalbu/.sdkman/candidates/java/25.0.4-tem scripts/verify.sh backend
```

Node 22.23.2, npm 12.0.2 e lockfile npm são reproduzidos nos runners. Playwright
usa o Chromium fixado pelo pacote. Docker/Testcontainers gerencia containers
isolados; não usar credenciais ou sandboxes nos testes da CI.

Actions são fixadas por SHA (`actions/checkout@11d5960a`, `setup-java@cf277c60`,
`setup-node@49933ea`, `upload-artifact@ea165f8d`), token somente leitura,
timeouts explícitos, concorrência por branch e artefatos retidos por sete
dias. Dependências são fixadas (`backend/pom.xml` com BOM, `frontend/
package-lock.json`, `npm@12.0.2`); a pipeline não faz merge, não publica
imagens e não altera configuração da máquina do usuário.

Dependabot (`.github/dependabot.yml`) propõe atualizações semanais para npm,
Maven e GitHub Actions (PRs pequenos, agrupamento por ecossistema, sem
auto-merge). Auditoria de dependências:

- Runtime (`--omit=dev --audit-level=high`): 0 vulnerabilidades — gate
  verificável em `scripts/verify.sh security`.
- Completo (dev incluso): 4 high em `js-yaml` 4.0.0-4.3.1 via
  `@hey-api/openapi-ts` → `@hey-api/json-schema-ref-parser`/`@hey-api/shared`
  (GHSA-52cp, GHSA-5p4m, GHSA-2883, CVE-2026-59870 sem backport). Dev-only
  (geração de cliente), sem impacto em runtime/browser; triagem registrada
  como risco aceito até correção upstream, correção via `npm audit fix --force`
  implicaria quebrar para 0.97.0.
- Backend: BOM Spring Boot 4.1.1 pinado; `mvn dependency:tree` sem divergência
  de versões; OWASP Dependency-Check requer NVD API key e não roda em CI
  sem segredo — limitação documentada, não alegado como verificado.

Segredos: `scripts/check-secrets.sh` varre arquivos rastreados (exceto
`node_modules`, `target`, `generated`, `api-reference` e o próprio script)
para padrões de alta confiança (`PRIVATE KEY`, `AKIA...`, `sk_live_`,
`ghp_...`); allowlist em `.secret-allowlist.txt` para falsos positivos.
PRs de fork não recebem segredos (`permissions: contents: read` no workflow
e nos jobs).

C13c entregue: política de commits (`commit-policy`), agenda semanal
(`schedule`) e proteção de `main` verificada no remoto. Jobs exigidos:
`backend`, `frontend`, `contracts`, `docs`, `security`, `commit-policy`,
`quality-gate`. Em mudança só documental o agregador mantém todos como
exigidos (sem `paths-ignore` que mascararia falha); docs-only não bypassa
backend/frontend/security — custo zero mantido com retenção 7 dias e
concorrência por branch.

Proteção de `main` (ver §3.1): exigidos `strict: true`, `contexts` acima e
`enforce_admins: false`; revisão humana de forma viável para projeto
individual (sem exigir 2 approvers). Verificada via `gh api
repos/{owner}/{repo}/branches/main/protection` após PR real; se indisponível
no plano gratuito, registrar limitação sem alegar proteção ativa. Neste
repositório, proteção foi configurada após verificação dos PRs #14–#16.

CD/release é C92a, após existir aplicação empacotada. Esta CI não comprova
homologação C04 (sandbox opt-in).
