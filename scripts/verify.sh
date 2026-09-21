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
  frontend)
    npm --prefix frontend run lint
    npm --prefix frontend run format:check
    npm --prefix frontend run test:ci
    npm --prefix frontend run build
    npm --prefix frontend run e2e:local
    ;;
  all)
    "$0" backend
    "$0" contracts
    "$0" frontend
    ;;
  *)
    echo 'Usage: scripts/verify.sh [backend|contracts|frontend|all]' >&2
    exit 2
    ;;
esac
