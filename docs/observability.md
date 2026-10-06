# Observabilidade HTTP — C12

## Critérios

- OBS-001: devolver `X-Request-ID` UUID e manter o mesmo `correlationId` no MDC
  durante o processamento; preservar UUID recebido válido, substituir cabeçalho
  inválido e restaurar o contexto anterior ao sair, inclusive em erro.
- OBS-002: emitir `http_request_completed` em JSON com método, template de rota,
  status e duração em milissegundos. Usar `UNMATCHED` para rota desconhecida.
  Nunca registrar URL bruta, query, body, cookies, autorização ou mensagem de
  exceção nesse evento. O ID é correlação, não identidade/autorização.
- OBS-003: expor apenas `/actuator/health`, `/actuator/health/liveness` e
  `/actuator/health/readiness` com status público e nomes dos grupos na raiz, sem detalhes de dependências.
  `env`, `metrics` e discovery não são públicos.

Isso responde: qual requisição falhou, qual rota/status e quanto demorou; o
processo aceita tráfego? Métricas internas do Actuator são coletadas, mas não
expostas publicamente. Traces Kafka e dashboards pertencem às etapas futuras.
Readiness atual cobre disponibilidade do processo; não comprova PostgreSQL,
Kafka, pagamento ou frete. Dependências serão incluídas quando os módulos forem
implementados. O filtro cobre o processamento MVC síncrono atual; propagação e
conclusão de Servlet async devem ser especificadas/testadas antes de seu uso.

## Executar e verificar

```bash
./backend/mvnw -f backend/pom.xml spring-boot:run -Dspring-boot.run.arguments=--server.port=18080
curl -i http://localhost:18080/actuator/health/readiness
curl -i -H 'X-Request-ID: 12345678-1234-1234-1234-123456789abc' http://localhost:18080/actuator/health
```

O log JSON contém `correlationId`, `method`, `route`, `status` e `durationMs`.
A resposta de health não é Problem Details: seu schema está no OpenAPI canônico.
`/api/v1/status` continua sendo somente seed WireMock de C11a.

Testes: `RequestCorrelationFilterTest` cobre correlação, contexto, erros e
exclusão de dados sensíveis; `HealthEndpointTest` usa servidor HTTP real com
porta aleatória para validar health e a ausência de endpoints administrativos.

Fontes: [logging estruturado](https://docs.spring.io/spring-boot/reference/features/logging.html)
e [probes](https://docs.spring.io/spring-boot/reference/actuator/endpoints.html).

## Evidência local de C12

Em 20/09/2026, o JAR empacotado executou em Temurin 25.0.4 na porta 18080.
Uma requisição com UUID conhecido retornou o mesmo `X-Request-ID`; o evento
JSON foi parseado e conferido sem token, cookie ou query enviados na sonda.
RSS observado: 232.596 KiB (aproximadamente 227 MiB), após a primeira sonda,
sem carga; é uma amostra local, não orçamento de produção ou benchmark.
Readiness recusando tráfego retorna 503 sem derrubar liveness, coberto por teste.

## Métricas de recuperação — C79 (parcial)

Expostas pelo perfil `worker` em `/actuator/metrics` (o perfil inclui
`health,metrics`). Os rótulos são só estados, nunca pedido, intent ou evento,
para manter a cardinalidade fixa.

| Métrica | Rótulo | Significado |
|---|---|---|
| `dlp.eventing.outbox.pending.count` | — | eventos da outbox ainda não publicados |
| `dlp.eventing.outbox.pending.oldest_age_seconds` | — | idade do evento pendente mais antigo |
| `dlp.eventing.outbox.pending.attempts` | — | tentativas acumuladas dos pendentes |
| `dlp.eventing.consumer.failures` | `state` = `RETRYING`/`QUARANTINED` | registros consumidos que falharam |
| `dlp.eventing.consumer.retrying.attempts` | — | tentativas gastas nos que ainda vão ser reprocessados |
| `dlp.payments.intents` | `status` = `UNKNOWN`/`UNDER_REVIEW`/`REFUND_REQUESTED` | intents que ainda exigem conciliação, análise ou reembolso |
| `dlp.payments.intents.oldest_age_seconds` | `status` (mesmos valores) | tempo desde que a intent mais antiga entrou no estado |
| `dlp.payments.operations.in_flight` | — | chamadas ao provedor reclamadas e ainda sem resultado gravado |
| `dlp.payments.provider_events` | `status` = `RECEIVED`/`REVIEW` | notificações aguardando processamento ou operador |

Correlação: o `correlationId` do request HTTP vai para o evento da outbox
(`EventEnvelope.correlationId`) e, no consumo, é colocado no MDC enquanto o
efeito roda, então os logs do efeito carregam o mesmo identificador; o
contexto anterior do worker é restaurado depois.

Ainda faltam nesta fatia: traces distribuídos (OpenTelemetry) e a
instrumentação de etiquetas de frete (C70) e de notificações (C75).

## Painel local — C79a

`docker compose --profile observability up -d` sobe Prometheus
(`prom/prometheus:v3.13.4`, `127.0.0.1:19090`) e Grafana
(`grafana/grafana:13.0.10`, `127.0.0.1:13000`). O Prometheus coleta
`/actuator/prometheus` do worker no host (`host.docker.internal:18081`); o
Grafana provisiona a fonte e o painel "Recuperação de compras"
(`infra/local/observability/grafana/dashboards/recovery.json`): outbox,
falhas do consumidor, pagamentos por estado e idade, chamadas em voo e
notificações do provedor.

Evidência local (2026-10-05): com o worker em `worker,local` contra um banco
isolado e o provedor simulado, uma intent `UNKNOWN` semeada recebeu três
consultas `NOT_FOUND` e foi para `UNDER_REVIEW` (`UNKNOWN_UNRESOLVED`); o
Prometheus mostrou o alvo `up`, `dlp_payments_intents{status="UNDER_REVIEW"} 1`
e `dlp_eventing_consumer_failures{state="QUARANTINED"} 1`, e o painel
respondeu 200 na API do Grafana. Consumo medido: Prometheus 26 MiB, Grafana
168 MiB, worker 413 MiB de RSS com `-Xmx384m`. Traces ficam para a C79.
