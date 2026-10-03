# Progresso — trilha de trabalho do fim do plano

Registro das sessões do Claude, que avança do fim do plano para o começo enquanto
o Codex avança do começo. Fica separado de `tasks/progress.md` para que dois
agentes não disputem o mesmo arquivo; ao integrar, copie o essencial para lá.
Mesmas regras do plano: só evidência verificada, sem segredo, sem PII.

## Sessão 2026-10-03 — C92 (parcial): imagens imutáveis de release

| Campo | Conteúdo |
|---|---|
| Base | worktree `/home/gaalbu/codigos/de-la-do-para-wt/c92-release-images`, branch `claude/c92-release-images` sobre `origin/main@5bf81bc`; árvore do Codex intocada |
| Tarefa | C92: Dockerfiles, versionamento e smoke das imagens; estado **parcial** (dependências C63, C73, C73a, C81, C81a, C81b e C83 abertas; profile de release com gateway fica para a C92a) |
| Mudanças | `infra/release/backend.Dockerfile` (API e worker, mesma imagem), `infra/release/frontend.Dockerfile` (SSR sem gerenciador de pacotes), listas de permissão `*.Dockerfile.dockerignore`, `scripts/release-smoke.sh`, `docs/release.md`; uma linha em `README.md`, `docs/PLANO-MESTRE.md` e `docs/traceability.md` |
| Verificação | Builds locais OK (backend 62 s, frontend 27 s). `DLP_SKIP_BUILD=1 scripts/release-smoke.sh` exit 0: sem root, `HEALTHCHECK`, rótulos, sem variável sensível/ferramentas de desenvolvimento/`.env` em `/app`; API saudável com 25 migrations em banco vazio, worker e frontend saudáveis, mesma imagem para API e worker. Negativo: rótulo de versão divergente → exit 1. JAR com SHA-256 idêntico em dois builds sem cache. Um build falhou com `ClassFormatError` em classe do JDK e passou ao repetir (RAM defeituosa conhecida) |
| Remoto | PR da branch `claude/c92-release-images` contra `main`; checks de CI e merge ficam registrados no próprio PR |
| Limite | Sem gateway nem jornada de compra sobre as imagens; healthcheck do frontend usa rota pré-renderizada; senha de desenvolvimento padrão permanece em `application.yml` (zona do Codex, não alterada); digests das bases são atualizados manualmente |
| Próximo passo | C92a: profile `release` no compose com gateway, workflow de entrega manual/por tag, checksums, SBOM e smoke no CI |
