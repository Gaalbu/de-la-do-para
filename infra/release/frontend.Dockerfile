# syntax=docker/dockerfile:1
# Imagem imutável do frontend Angular com SSR (C92). Contexto de build: raiz do
# repositório, porque o cliente da API é gerado de contracts/openapi/v1.yaml.
#
#   docker build -f infra/release/frontend.Dockerfile -t de-la-do-para/frontend:<versão> .

ARG NODE_IMAGE=node:22.23.2-bookworm-slim@sha256:48e4b67d85f87bd551df43704e24d252f56cc5f8e9718841aace50f19948f0f9

FROM ${NODE_IMAGE} AS build
WORKDIR /src
COPY frontend/package.json frontend/package-lock.json frontend/
RUN --mount=type=cache,id=dlp-npm,target=/root/.npm npm ci --prefix frontend
COPY contracts/openapi/v1.yaml contracts/openapi/v1.yaml
COPY frontend frontend
RUN npm --prefix frontend run contracts:generate \
 && npm --prefix frontend run build

FROM ${NODE_IMAGE} AS runtime
ARG VERSION=0.0.0-local
ARG REVISION=unknown
LABEL org.opencontainers.image.title="De Lá do Pará — frontend SSR" \
      org.opencontainers.image.description="Angular com renderização no servidor" \
      org.opencontainers.image.source="https://github.com/Gaalbu/de-la-do-para" \
      org.opencontainers.image.version="${VERSION}" \
      org.opencontainers.image.revision="${REVISION}"
# O runtime só executa o bundle: sem gerenciadores de pacotes.
RUN rm -rf /usr/local/lib/node_modules /usr/local/bin/npm /usr/local/bin/npx \
           /opt/yarn-* /usr/local/bin/yarn /usr/local/bin/yarnpkg
WORKDIR /app
COPY --from=build /src/frontend/dist/frontend /app
ENV NODE_ENV=production PORT=4000
USER node
EXPOSE 4000
HEALTHCHECK --interval=10s --timeout=3s --start-period=20s --retries=6 \
  CMD ["node", "-e", "fetch('http://127.0.0.1:4000/').then((r) => process.exit(r.status < 500 ? 0 : 1), () => process.exit(1))"]
ENTRYPOINT ["node", "/app/server/server.mjs"]
