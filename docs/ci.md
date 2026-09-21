# CI — C13 e C13a

A pipeline `.github/workflows/ci.yml` executa em PRs, push em `main` e disparo
manual. Jobs `backend`, `frontend`, `contracts` e `docs` usam os mesmos
comandos do ambiente local. `quality-gate` exige sucesso de todos, inclusive
em mudança só documental; falha, cancelamento e skip não passam.

```bash
npm ci --prefix frontend
# Docker ativo para PostgreSQL/Kafka dos testes de integração:
scripts/verify.sh backend
scripts/verify.sh docs
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

Actions são fixadas por SHA, token somente leitura, timeouts explícitos,
concorrência por branch e artefatos retidos por sete dias. A pipeline não faz
merge, não publica imagens e não altera configuração da máquina do usuário.

C13a entregue: gate `docs` com verificação de links e contratos. C13b–C13c
ainda acrescentarão scans de segurança, política de commits/atualizações e
proteção de main verificada no remoto. CD/release é C92a, após existir
aplicação empacotada para entrega. Esta CI não comprova essas etapas nem a
homologação C04.
