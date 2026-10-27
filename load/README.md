# Cargas k6 — C90 (parcial)

Cenários de carga reproduzíveis, apontados somente ao ambiente local isolado.

## Catálogo (`catalog.js`)

Somente leitura: lista `GET /api/v1/products?size=20` e abre um produto
sorteado em `GET /api/v1/products/{slug}`. Taxa de chegada de 10 req/s por
2 min, com subida e descida.

```bash
./backend/mvnw -f backend/pom.xml spring-boot:run -Dspring-boot.run.arguments=--server.port=18080
k6 run -e BASE_URL=http://127.0.0.1:18080 load/catalog.js
```

Limiares: erro HTTP < 1% e p95 < 500 ms por rota. São pontos de partida da
definição, não orçamentos medidos; a medição pertence à C91.
O script recusa `BASE_URL` fora de `127.0.0.1`/`localhost`.

## Limites desta fatia

- Não executado: `k6` não está instalado no ambiente de autoria.
- Falta o cenário de checkout, o dataset, o profile de carga no Compose e a
  verificação de invariantes no banco após a carga (disputa pela última
  unidade, medida à parte do throughput). C90 permanece aberta e depende da C88.
