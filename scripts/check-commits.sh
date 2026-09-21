#!/usr/bin/env bash
set -euo pipefail
# C13c: verifica Conventional Commits no intervalo do PR ou nos últimos commits.
# Aceita: feat, fix, test, docs, refactor, build, ci, chore com escopo opcional.
root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
base="${1:-origin/main}"
head="${2:-HEAD}"

# Determina lista de commits: se base..head tiver commits, usa; senão verifica HEAD.
range="$base..$head"
if ! git -C "$root" rev-parse --verify "$base" >/dev/null 2>&1; then
  range="HEAD~1..HEAD"
fi
commits="$(git -C "$root" log --oneline "$range" 2>/dev/null || git -C "$root" log --oneline -1)"
if [ -z "$commits" ]; then
  echo "OK: nenhum commit novo para verificar"
  exit 0
fi

re='^(feat|fix|test|docs|refactor|build|ci|chore)(\([a-z0-9._-]+\))?: .{1,72}$'
bad=0
while IFS= read -r line; do
  # extrai apenas a mensagem (após hash)
  msg="$(echo "$line" | sed -E 's/^[a-f0-9]+ //')"
  # ignora merges
  if echo "$msg" | grep -qE '^Merge (pull request|branch) '; then
    continue
  fi
  if ! echo "$msg" | grep -Eq "$re"; then
    echo "ERRO: commit fora do padrão Conventional Commits: $line" >&2
    echo "  esperado: type(scope)?: descrição  types: feat|fix|test|docs|refactor|build|ci|chore" >&2
    bad=1
  fi
done <<< "$commits"

if [ "$bad" -ne 0 ]; then
  exit 1
fi
count="$(echo "$commits" | wc -l)"
echo "OK: $count commit(s) no padrão Conventional Commits"
