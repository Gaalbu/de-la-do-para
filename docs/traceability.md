# Matriz de rastreio — requisito → spec → teste → commit → evidência

Criada em C02; atualizada a cada entrega, somente após evidência verificada.
Planejamento aprovado não significa teste aprovado. Sem tokens, CPF, senhas
ou payloads pessoais.

| Requisito / tarefa | Spec | Teste / verificação | Commit(s) | Evidência |
|---|---|---|---|---|
| C00a — repo e pré-requisitos | Plano §2 | `ls`, `git status`, versões de ferramentas | `7437919` (parte) | `tasks/progress.md` sessão bootstrap |
| C00b — plano no clone | Plano §2 | `git remote -v`, `sha256sum` idêntico (`c67d245e…`) | `7437919` | `docs/PLANO-MESTRE.md` + push `main`, repo PUBLIC |
| C01 — escopo e mapa | `docs/scope.md` | Revisão automatizada: D 64/64, A 10/10, módulos 12/12, figuras 26/26; `git diff --check`; PR CLEAN/MERGEABLE | `435487c`, merge `b9e70f8` | PR #1 merged; `tasks/progress.md` sessão C01 |
| C02 — convenções SDD/ADR | `docs/contributing.md` (+ templates) | DOC: links, `git diff --check`, exemplo preenchido | _(este commit)_ | PR a referenciar |

Evidências por release vivem em `docs/evidence/<marco-ou-release>/`
(criado quando houver a primeira entrega executável).
