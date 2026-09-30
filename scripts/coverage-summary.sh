#!/usr/bin/env bash
set -euo pipefail
# D38: resume a cobertura de branches do JaCoCo por módulo do backend, destacando
# os críticos (checkout, payments, inventory, pricing; meta 80%). Só relata: não
# falha o build enquanto a meta não for ativada com decisão do usuário.
root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
export LC_ALL=C
csv="${1:-$root/backend/target/site/jacoco/jacoco.csv}"
if [ ! -f "$csv" ]; then
  echo "ERRO: relatório JaCoCo ausente em $csv (rode scripts/verify.sh backend)" >&2
  exit 1
fi

echo "| Módulo | Branches cobertas | Cobertura de branches | Meta D38 |"
echo "|---|---|---|---|"
awk -F, 'NR > 1 {
  split($2, parts, ".")
  module = (parts[4] == "" ? "(raiz)" : parts[4])
  missed[module] += $6; covered[module] += $7
}
END {
  critical["checkout"]; critical["payments"]; critical["inventory"]; critical["pricing"]
  for (m in covered) {
    total = missed[m] + covered[m]
    if (total == 0) continue
    pct = sprintf("%.1f%%", 100 * covered[m] / total)
    goal = (m in critical) ? (covered[m] / total >= 0.8 ? "80% atingida" : "80% pendente") : ""
    printf "| %s | %d/%d | %s | %s |\n", m, covered[m], total, pct, goal
  }
}' "$csv" | sort
