#!/usr/bin/env bash
set -euo pipefail
# C13b: varredura local de segredos sem enviar dados a serviço externo.
# Falha se padrão sensível aparecer fora de allowlist/exemplos.
root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
allowlist="$root/.secret-allowlist.txt"

# Padrões de alta confiança (evitar falsos positivos de .env.example/placeholders).
patterns=(
  "BEGIN (RSA |DSA |EC |OPENSSH )?PRIVATE KEY"
  "AKIA[0-9A-Z]{16}"
  "sk_live_[0-9a-zA-Z]{16,}"
  "ghp_[A-Za-z0-9]{36,}"
)

tmp="$(mktemp)"
trap 'rm -f "$tmp"' EXIT

# Lista de arquivos rastreados + não ignorados pelo .gitignore (melhor que scan em node_modules/dist)
git -C "$root" ls-files -z --cached --others --exclude-standard | tr '\0' '\n' | while IFS= read -r f; do
  case "$f" in
    .git/*|frontend/node_modules/*|frontend/dist/*|frontend/.angular/*|backend/target/*|frontend/src/generated/*|frontend/api-reference/*|scripts/check-secrets.sh|.secret-allowlist.txt) continue ;;
    *) echo "$f" ;;
  esac
done > "$tmp.list"

found=0
while IFS= read -r file; do
  [ -f "$root/$file" ] || continue
  for pat in "${patterns[@]}"; do
    if grep -En -i "$pat" "$root/$file" 2>/dev/null | grep -v -F -f "$allowlist" 2>/dev/null | grep -q .; then
      echo "ERRO: padrão sensível '$pat' em $file:" >&2
      grep -En -i "$pat" "$root/$file" | grep -v -F -f "$allowlist" | sed 's/^/  /' >&2
      found=1
    fi
  done
done < "$tmp.list"

if [ "$found" -ne 0 ]; then
  echo "Falha: segredo potencial detectado (ver allowlist $allowlist para falsos positivos)." >&2
  exit 1
fi
echo "OK: nenhuma assinatura de segredo detectada nos arquivos rastreados"
