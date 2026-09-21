# CI inicial — C13

A pipeline `.github/workflows/ci.yml` executa em PRs, push em `main` e disparo
manual. Jobs `backend`, `frontend` e `contracts` usam os mesmos comandos do
ambiente local. `quality-gate` exige sucesso de todos, inclusive em mudança só
documental nesta primeira versão; falha, cancelamento e skip não passam.

```bash
npm ci --prefix frontend
# Docker ativo para PostgreSQL/Kafka dos testes de integração:
scripts/verify.sh backend
scripts/verify.sh contracts
scripts/verify.sh frontend
# ou todos, sequencialmente:
scripts/verify.sh
```

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

C13a–C13c ainda acrescentarão validação documental abrangente, scans de
segurança, política de commits/atualizações e proteção de main verificada no
remoto. CD/release é C92a, após existir aplicação empacotada para entrega.
Esta CI inicial não comprova essas etapas nem a homologação C04.
