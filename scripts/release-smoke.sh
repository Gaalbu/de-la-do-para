#!/usr/bin/env bash
# Smoke das imagens de release (C92): constrói API/worker e frontend, inspeciona
# usuário, ferramentas e segredos e sobe os três processos numa rede Docker
# privada, sem publicar portas no host nem usar os nomes do compose local.
#
#   scripts/release-smoke.sh                 # constrói e verifica
#   DLP_SKIP_BUILD=1 scripts/release-smoke.sh  # usa as imagens já construídas
#
# Não executa compra nem fala com sandbox: prova que as imagens iniciam, migram o
# banco e ficam saudáveis. O gate de comportamento continua sendo `verify`.
set -euo pipefail
cd "$(dirname "$0")/.."

VERSION="${DLP_RELEASE_VERSION:-0.0.0-local}"
REVISION="$(git rev-parse HEAD)"
BACKEND_IMAGE="de-la-do-para/backend:${VERSION}"
FRONTEND_IMAGE="de-la-do-para/frontend:${VERSION}"
RUN="dlp-release-smoke-$$"
NET="${RUN}-net"
POSTGRES_IMAGE="$(sed -n 's/^    image: \(postgres:.*\)$/\1/p' compose.yml)"
KAFKA_IMAGE="$(sed -n 's/^    image: \(apache\/kafka:.*\)$/\1/p' compose.yml)"

cleanup() {
  docker rm -f "${RUN}-postgres" "${RUN}-kafka" "${RUN}-api" "${RUN}-worker" "${RUN}-frontend" >/dev/null 2>&1 || true
  docker network rm "$NET" >/dev/null 2>&1 || true
}
trap cleanup EXIT

fail() { echo "FALHA: $*" >&2; exit 1; }
ok() { echo "ok: $*"; }

wait_healthy() {
  local name="$1" limit="${2:-150}" status
  for _ in $(seq "$limit"); do
    status="$(docker inspect -f '{{.State.Status}} {{if .State.Health}}{{.State.Health.Status}}{{end}}' "$name")"
    case "$status" in
      "running healthy") ok "$name saudável"; return 0 ;;
      exited*|dead*) break ;;
    esac
    sleep 1
  done
  docker logs --tail 40 "$name" >&2 || true
  fail "$name não ficou saudável (último estado: $status)"
}

if [ "${DLP_SKIP_BUILD:-0}" != "1" ]; then
  docker build -f infra/release/backend.Dockerfile -t "$BACKEND_IMAGE" \
    --build-arg VERSION="$VERSION" --build-arg REVISION="$REVISION" \
    --build-arg BUILD_TIMESTAMP="$(git log -1 --format=%cI)" .
  docker build -f infra/release/frontend.Dockerfile -t "$FRONTEND_IMAGE" \
    --build-arg VERSION="$VERSION" --build-arg REVISION="$REVISION" .
fi

for image in "$BACKEND_IMAGE" "$FRONTEND_IMAGE"; do
  [ "$(docker run --rm --entrypoint id "$image" -u)" != "0" ] || fail "$image executa como root"
  [ -n "$(docker image inspect -f '{{.Config.User}}' "$image")" ] || fail "$image não declara USER"
  [ -n "$(docker image inspect -f '{{if .Config.Healthcheck}}declared{{end}}' "$image")" ] \
    || fail "$image não declara HEALTHCHECK"
  [ "$(docker image inspect -f '{{index .Config.Labels "org.opencontainers.image.revision"}}' "$image")" = "$REVISION" ] \
    || fail "$image não registra o commit $REVISION"
  [ "$(docker image inspect -f '{{index .Config.Labels "org.opencontainers.image.version"}}' "$image")" = "$VERSION" ] \
    || fail "$image não registra a versão $VERSION"
  if docker image inspect -f '{{range .Config.Env}}{{println .}}{{end}}' "$image" \
      | cut -d= -f1 | grep -Eiq 'PASSWORD|SECRET|TOKEN|KEY|CREDENTIAL'; then
    fail "$image embute variável de ambiente sensível"
  fi
  docker run --rm --entrypoint bash "$image" -c '
    for tool in mvn mvnw npm npx yarn git javac gcc make; do
      ! command -v "$tool" >/dev/null || { echo "ferramenta de desenvolvimento presente: $tool"; exit 1; }
    done
    [ -z "$(find /app \( -name ".env*" -o -name "*.pem" -o -name "*.key" \) 2>/dev/null)" ]
  ' || fail "$image contém ferramenta de desenvolvimento ou arquivo de segredo"
  ok "$image: sem root, com healthcheck, rótulos, sem ferramentas de desenvolvimento nem segredos"
done

DB_PASSWORD="$(openssl rand -hex 16)"
PICKUP_KEY="$(openssl rand -base64 32)"

docker network create "$NET" >/dev/null
docker run -d --name "${RUN}-postgres" --network "$NET" --network-alias postgres \
  -e POSTGRES_DB=deladopara -e POSTGRES_USER=deladopara -e POSTGRES_PASSWORD="$DB_PASSWORD" \
  --health-cmd 'pg_isready -U deladopara -d deladopara' --health-interval 2s --health-retries 30 \
  "$POSTGRES_IMAGE" >/dev/null
docker run -d --name "${RUN}-kafka" --network "$NET" --network-alias kafka --memory 1g \
  -e CLUSTER_ID=MkU3OEVBNTcwNTJENDM2Qk -e KAFKA_NODE_ID=1 -e KAFKA_PROCESS_ROLES=broker,controller \
  -e KAFKA_LISTENERS=PLAINTEXT://:29092,CONTROLLER://:29093 \
  -e KAFKA_ADVERTISED_LISTENERS=PLAINTEXT://kafka:29092 \
  -e KAFKA_LISTENER_SECURITY_PROTOCOL_MAP=CONTROLLER:PLAINTEXT,PLAINTEXT:PLAINTEXT \
  -e KAFKA_CONTROLLER_LISTENER_NAMES=CONTROLLER -e KAFKA_CONTROLLER_QUORUM_VOTERS=1@kafka:29093 \
  -e KAFKA_OFFSETS_TOPIC_REPLICATION_FACTOR=1 -e KAFKA_TRANSACTION_STATE_LOG_REPLICATION_FACTOR=1 \
  -e KAFKA_TRANSACTION_STATE_LOG_MIN_ISR=1 -e KAFKA_HEAP_OPTS='-Xms256m -Xmx512m' \
  --health-cmd '/opt/kafka/bin/kafka-topics.sh --bootstrap-server kafka:29092 --list' \
  --health-interval 5s --health-retries 24 \
  "$KAFKA_IMAGE" >/dev/null
wait_healthy "${RUN}-postgres" 60
wait_healthy "${RUN}-kafka" 120

BACKEND_ENV=(
  -e SPRING_DATASOURCE_URL=jdbc:postgresql://postgres:5432/deladopara
  -e SPRING_DATASOURCE_USERNAME=deladopara -e SPRING_DATASOURCE_PASSWORD="$DB_PASSWORD"
  -e SHIPPING_PICKUP_ENCRYPTION_KEY="$PICKUP_KEY"
)
# A API migra o banco primeiro; o worker só sobe depois, sem disputar o Flyway.
docker run -d --name "${RUN}-api" --network "$NET" --memory 1g "${BACKEND_ENV[@]}" "$BACKEND_IMAGE" >/dev/null
wait_healthy "${RUN}-api" 180
migrations="$(docker exec "${RUN}-postgres" psql -U deladopara -d deladopara -Atc \
  'select count(*) from flyway_schema_history where success')"
[ "${migrations:-0}" -gt 0 ] || fail "a API ficou saudável sem aplicar migrations"
ok "API aplicou ${migrations} migrations no banco vazio"
docker run -d --name "${RUN}-worker" --network "$NET" --memory 1g "${BACKEND_ENV[@]}" \
  -e SPRING_PROFILES_ACTIVE=worker -e APP_EVENTING_BOOTSTRAP_SERVERS=kafka:29092 \
  -e APP_EVENTING_TOPIC=events.outbound -e APP_EVENTING_LEASE=PT30S \
  -e APP_EVENTING_BATCH_SIZE=10 -e APP_EVENTING_POLL_DELAY=PT1S \
  "$BACKEND_IMAGE" >/dev/null
wait_healthy "${RUN}-worker" 180
docker run -d --name "${RUN}-frontend" --network "$NET" "$FRONTEND_IMAGE" >/dev/null
wait_healthy "${RUN}-frontend" 60

[ "$(docker inspect -f '{{.Image}}' "${RUN}-api")" = "$(docker inspect -f '{{.Image}}' "${RUN}-worker")" ] \
  || fail "API e worker não usam o mesmo artefato"
ok "API e worker executam a mesma imagem (${BACKEND_IMAGE})"
echo "Smoke de release concluído para ${REVISION} (${VERSION})."
