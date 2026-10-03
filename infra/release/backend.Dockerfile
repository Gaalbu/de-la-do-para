# syntax=docker/dockerfile:1
# Imagem imutável da API e do worker (C92). O mesmo artefato executa os dois
# papéis: o perfil Spring `worker` (SPRING_PROFILES_ACTIVE=worker) seleciona o
# processo de eventos e pagamentos. Contexto de build: raiz do repositório.
#
#   docker build -f infra/release/backend.Dockerfile -t de-la-do-para/backend:<versão> .

ARG JDK_IMAGE=eclipse-temurin:25.0.4_7-jdk@sha256:97014c4b396021f9ddb7d592a7dbedb0c4e4215c29e03dc01c393558aefb71c2
ARG JRE_IMAGE=eclipse-temurin:25.0.4_7-jre@sha256:bb036ed6cfdc57e3da7c22634d15f1b840d2caf76183861c80e81ca4b5104abb

FROM ${JDK_IMAGE} AS build
# Timestamp fixo no JAR: o mesmo commit gera o mesmo artefato.
ARG BUILD_TIMESTAMP=1980-01-01T00:00:02Z
WORKDIR /src
COPY backend/mvnw backend/pom.xml backend/
COPY backend/.mvn backend/.mvn
COPY contracts/events/envelope.schema.json contracts/events/envelope.schema.json
COPY backend/src/main backend/src/main
# Testes, formatação e análise estática pertencem ao gate `verify` do CI; a imagem
# só empacota o que esse gate já aprovou.
RUN --mount=type=cache,id=dlp-m2,target=/root/.m2 \
    ./backend/mvnw -f backend/pom.xml --batch-mode --no-transfer-progress \
      -Dmaven.test.skip=true -Dproject.build.outputTimestamp="${BUILD_TIMESTAMP}" package \
 && mkdir /out \
 && cp "$(find backend/target -maxdepth 1 -name '*.jar' ! -name '*.original')" /out/app.jar

FROM ${JRE_IMAGE} AS runtime
ARG VERSION=0.0.0-local
ARG REVISION=unknown
LABEL org.opencontainers.image.title="De Lá do Pará — API e worker" \
      org.opencontainers.image.description="Spring Boot: API (padrão) e worker (perfil worker)" \
      org.opencontainers.image.source="https://github.com/Gaalbu/de-la-do-para" \
      org.opencontainers.image.version="${VERSION}" \
      org.opencontainers.image.revision="${REVISION}"
RUN groupadd --system --gid 10001 app \
 && useradd --system --uid 10001 --gid app --no-create-home --shell /usr/sbin/nologin app \
 && install -d -o app -g app /var/lib/deladopara/media
WORKDIR /app
COPY --from=build /out/app.jar /app/app.jar
ENV APP_MEDIA_DIRECTORY=/var/lib/deladopara/media
USER 10001:10001
EXPOSE 8080
VOLUME ["/var/lib/deladopara/media"]
# A imagem não traz curl nem wget; o bash basta para consultar a readiness.
HEALTHCHECK --interval=10s --timeout=3s --start-period=60s --retries=6 \
  CMD ["bash", "-c", "exec 3<>/dev/tcp/127.0.0.1/8080 && printf 'GET /actuator/health/readiness HTTP/1.0\\r\\n\\r\\n' >&3 && grep -q '\"status\":\"UP\"' <&3"]
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75", "-XX:+ExitOnOutOfMemoryError", "-jar", "/app/app.jar"]
