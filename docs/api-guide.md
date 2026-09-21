# Guia da API — De Lá do Pará (C11a)

Contrato canônico: `contracts/openapi/v1.yaml`. Referência interativa gerada
dele; exemplos com dados fictícios, variáveis e sem segredos. **Só o que
está marcado como executável funciona**; cenários futuros estão
explicitamente sinalizados e não devem ser apresentados como funcionais.

## Referência interativa local

Gerada do contrato (Redoc estático, verificado em C11a):

```bash
npx --prefix frontend redocly build-docs contracts/openapi/v1.yaml -o frontend/api-reference/index.html
python3 -m http.server 18080 --directory frontend/api-reference
# abrir http://localhost:18080
```

`frontend/api-reference/` é gerado e ignorado no Git; `contracts:lint`
barra spec inválida antes de publicar.

## Gates de contrato (C11b)

Duas direções, ambas verdes no seed e ambas comprovadas com fixture
divergente (falham pelo motivo esperado):

- **Implementação → contrato** (`RouteContractCoverageTest`, backend):
  toda rota HTTP implementada precisa existir no contrato canônico ou na
  allowlist `contracts/openapi/route-allowlist.properties`. Só endpoints
  de infraestrutura entram na allowlist, com justificativa em comentário;
  regra de negócio sem contrato nunca. Exceção atual: `/error`
  (`BasicErrorController` do Spring, sem regra comercial).
- **Exemplos → schema** (`redocly.yaml` + `contracts:lint`): `extends:
  recommended` (mantém os 3 warnings documentados em C11) com
  `no-invalid-schema-examples: error` — exemplo fora do schema reprova.
  O `--config ../redocly.yaml` explícito no script garante que a regra
  vale quando o lint roda de `frontend/`.

Cada endpoint futuro (C15+) amplia a cobertura no próprio PR
(contract-first): caminho no `v1.yaml`, exemplos válido/inválido e teste
HTTP, conforme `docs/spec-template.md` §8–§9.

## Convenções (resumo de API-E-TESTES.md)

- Dinheiro em centavos inteiros + moeda nos contratos; exibição BRL.
- Datas de eventos em UTC; exibição pt-BR; validade com zona comercial.
- Paginação com limites, filtros combináveis e ordenação estável.
- Erros em Problem Details + `codigo` comercial estável + `correlationId`.
- Checkout idempotente via `Idempotency-Key` (mesma chave + intenção =
  mesmo pedido; corpo diferente = 409) — futuro (C58a).
- Sessões em cookie protegido + CSRF; 401/403 sem expor credenciais —
  futuro (C15+).
- Eventos em envelope versionado (`contracts/events/`) — contratos por
  módulo chegam em C45.

## Exemplos

| Exemplo | Arquivo | Estado |
|---|---|---|
| Saúde do processo e probes | `docs/observability.md` | API real em C12 (`/actuator/health`, `/liveness`, `/readiness`) |
| Administração de produtores fictícios | `contracts/openapi/v1.yaml` (`listAdminProducers`, `createAdminProducer`, `getAdminProducer`, `updateAdminProducer`) | Executável em C19; requer sessão admin + CSRF para escritas; `demonstration: true`; sem operação DELETE |
| Seed de saúde | `contracts/openapi/examples/status.http` | Executável agora via **stub de contrato** no WireMock (`:18443`); não implementado na API real; usar os endpoints Actuator de C12 |
| Checkout idempotente | mesmo arquivo (comentado) | Futuro — ilustrativo, não executar |

Equivalente curl do exemplo executável:

```bash
curl -s http://localhost:18443/api/v1/status
# {"status":"ok","version":"0.0.1"}
```

## Critérios herdados por cada endpoint futuro (D63)

Método, caminho, propósito, permissões, parâmetros, schemas, exemplos de
sucesso **e** erro, status e cabeçalhos — no mesmo commit do comportamento,
com teste HTTP e contrato (ver `docs/spec-template.md` §8–§9).

C12: o harness percorre todos os mapeamentos MVC, inclusive os adicionados pelo Actuator. Probes são verificados também por HTTP real em `HealthEndpointTest`; convenções e correlação em [observability.md](observability.md).
