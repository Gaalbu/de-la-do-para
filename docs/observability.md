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
