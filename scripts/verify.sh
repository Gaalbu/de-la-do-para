#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "${BASH_SOURCE[0]}")/.."
case "${1:-all}" in
  backend)
    ./backend/mvnw -B -f backend/pom.xml verify
    ;;
  contracts)
    npm --prefix frontend run contracts:check
    ;;
  docs)
    npm --prefix frontend run docs:check
    npm --prefix frontend run contracts:check
    ;;
  security)
    ./scripts/check-secrets.sh
    npm --prefix frontend audit --omit=dev --audit-level=high
    echo "Nota: audit completo (dev incluso) apresenta 4 high em js-yaml via @hey-api/openapi-ts (GHSA-52cp, GHSA-5p4m, GHSA-2883); dev-only, sem runtime, triagem em docs/ci.md"
    ;;
  commits)
    ./scripts/check-commits.sh origin/main HEAD
    ;;
  frontend)
    # Frontend features consume generated OpenAPI types in clean checkouts.
    npm --prefix frontend run contracts:generate
    npm --prefix frontend run lint
    npm --prefix frontend run format:check
    npm --prefix frontend run test:ci
    npm --prefix frontend run build
    npm --prefix frontend run e2e:local
    ;;
  all)
    "$0" backend
    "$0" docs
    "$0" security
    "$0" commits
    "$0" contracts
    "$0" frontend
    ;;
  *)
    echo 'Usage: scripts/verify.sh [backend|contracts|frontend|all]' >&2
    exit 2
    ;;
esac
