# Registro de progresso — De Lá do Pará

Documento operacional previsto no plano mestre (§1 “Registro para continuidade”).
Atualizar ao final de cada sessão, somente após evidência verificada.

## Sessão 2026-09-20 — bootstrap C00a/C00b

| Campo | Conteúdo |
|---|---|
| Base | branch `main`, sem commits iniciais (repo vazio); plano `docs/PLANO-MESTRE.md` versão consolidação 20/09/2026, sha256 `c67d245e4d7758b4a7931fd16b5e7049651fc859bfb5aaf47cf2f4362f88efc6` |
| Tarefa | C00a (pré-clone) concluída nesta sessão; C00b (preservar plano no clone) em execução |
| Mudanças | Repo público `Gaalbu/de-la-do-para` criado vazio via `gh`; clone em `/home/gaalbu/codigos/de-la-do-para`; plano copiado para `docs/PLANO-MESTRE.md` com hash idêntico ao original; criados `tasks/progress.md`, `.gitignore`, `.env.example`, `README.md`. Nenhum código LAPES copiado; nenhum histórico reutilizado |
| Verificação | `git clone https://github.com/Gaalbu/de-la-do-para.git` (repo vazio, exit 0); `git remote -v` confere origin `Gaalbu/de-la-do-para`; destino `de-la-do-para` não existia antes (verificado com `ls`); `sha256sum` do plano original e da cópia idênticos; `git status --short` limpo antes do commit |
| Remoto | https://github.com/Gaalbu/de-la-do-para — primeiro commit documental ainda a enviar nesta sessão; resultado local separado de CI (sem CI ainda) |
| Próximo passo | C01 — `docs(scope)`: transcrever escopo aprovado e mapa de capacidades (D01–D64) sem inferência; depois C02 convenções SDD/ADR |
| Perguntas | Namespace Java proposto `br.com.deladopara` confirmado pelo usuário; licença do código público e destino de releases seguem pendentes para a etapa de preparação de repo/release (não bloqueiam C01) |

## Ambiente registrado (C00a)

- Git 2.43.0; Docker Compose v5.5.1 (Docker 29.8.1); JDK 25.0.4 GraalVM CE; Node v22.23.2; npm 12.0.2 (versão a revalidar no bootstrap frontend C07, matriz Angular 22).
- RAM ~15 Gi disponível; portas locais em uso observadas: 5432 (PostgreSQL), 6379 (Redis), 8080 — o profile `local` do Compose usará portas/volumes próprios do projeto (C08).
- `/home/gaalbu/codigos` não é repo git (pasta de projetos); nenhum reset/clean/force executado; pasta `tasks/` pré-existente em `/home/gaalbu/codigos` não foi tocada.
