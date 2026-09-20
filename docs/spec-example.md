# Spec de exemplo (documental) — transcrição do escopo, C01

> Exemplo preenchido do [`spec-template.md`](spec-template.md), criado em
> C02 para demonstrar como referenciar teste e evidência. Não é spec de
> módulo de aplicação; nenhuma spec de módulo foi aprovada por inferência.

## 0. Metadados

- Módulo: — (capacidade documental C01)
- Status: aprovada (revisão humana + merge do PR #1)
- Decisões base: D01–D64 transcritas; Q01 encaminhada para C03
- Personas: executor do plano, usuário revisor

## 1. Objetivo ◆

Transcrever escopo aprovado e mapa de capacidades para `docs/scope.md` e
`docs/decisions.md`, sem inferências novas, de modo que o usuário revise o
mapa antes das specs de módulos.

## 2. Comandos ◆

```bash
git diff --check
grep -rniE "gho_|github_pat|BEGIN (RSA )?PRIVATE KEY" --exclude-dir=.git .
gh pr view 1 --json state,mergeable,mergeStateStatus
```

## 3. Estrutura ◆

`docs/scope.md`, `docs/decisions.md`. Nada além disso (progresso registrado
em commit próprio `fab0fd3`).

## 4. Estilo e convenções ◆

Transcrição fiel ao plano; valores numéricos copiados, não arredondados nem
convertidos; perguntas pendentes encaminhadas com momento, nunca respondidas
por silêncio.

## 5. Estratégia de testes ◆

Verificação documental automatizada (script Python ad hoc): presença de cada
ID D01–D64 e A01–A10, dos 12 módulos e de 26 figuras-chave; spot-check de
D54/D22 contra o plano; estado do PR via `gh`.

## 6. Limites de atuação ◆

O exemplo não aprova specs de módulos nem fecha Q01 (momento: C03).

## 9. Critérios de aceite

| ID | Critério | Teste(s) | Evidência |
|---|---|---|---|
| SCOPE-001 | Todas as decisões D01–D64 transcritas | script de presença 64/64 (100%) | sessão C01 em `tasks/progress.md` |
| SCOPE-002 | Mapa com 12 módulos e dependências | script 12/12 + revisão humana | PR #1 merged (`b9e70f8`) |
| SCOPE-003 | Sem segredos nem links quebrados | grep de segredos vazio; links conferidos | `git diff --check` limpo |
