# Guia local — De Lá do Pará

Ambiente determinístico (C08): PostgreSQL, Kafka KRaft, Mailpit e WireMock
(simuladores HTTP). Funciona sem contas externas depois de baixar imagens.
Sandbox real é opt-in e separado (ver `docs/integrations/homologation.md`).

## Portas do host (próprias; 5432/6379/8080 ocupados na referência)

| Serviço | Host → container | Conferir |
|---|---|---|
| PostgreSQL 18.6 | `55432` → 5432 | `pg_isready -h localhost -p 55432` |
| Kafka 4.3.1 (KRaft, 1 nó) | `59092` → 9092 | `kafka-topics.sh --bootstrap-server localhost:59092 --list` (ou via container) |
| Mailpit SMTP / HTTP | `11025` → 1025, `18025` → 8025 | <http://localhost:18025> |
| WireMock | `18443` → 8080 | <http://localhost:18443/__admin/health> |

Tudo configurável via `.env` (ver `.env.example`).

## Subir, verificar, derrubar

```bash
cp .env.example .env   # ajuste POSTGRES_PASSWORD; resto tem padrão
docker compose --profile local up -d
docker compose --profile local ps          # todos healthy
docker compose --profile local down        # preserva volumes e dados
```

`down` **não** apaga dados. Reset total (destrutivo, explícito):

```bash
docker compose --profile local down -v
```

## Worker de outbox

O publisher Kafka não é iniciado no perfil padrão da API. Para executar o
processo separado, use `--spring.profiles.active=worker` e forneça todos os
parâmetros `APP_EVENTING_BOOTSTRAP_SERVERS`, `APP_EVENTING_TOPIC`,
`APP_EVENTING_LEASE`, `APP_EVENTING_BATCH_SIZE` e `APP_EVENTING_POLL_DELAY`.
Eles não têm valores padrão enquanto a C45 não aprovar lease e polling; o
worker falha cedo se a configuração estiver incompleta.

## Superfícies expostas

Só as portas acima, só em `localhost`. Banco, broker e simuladores ficam na
rede `de-la-do-para_default` interna. O túnel Cloudflare (aprovado para
webhooks) é **manual e posterior**: expõe somente `/api/v1/webhooks/*`
quando a API existir — nunca banco, broker, Mailpit ou métricas.

## Proxy do frontend

`frontend/proxy.conf.json` encaminha `/api` → `http://localhost:8080`
(uso com `ng serve --proxy-config proxy.conf.json`; origem única
navegador→Angular, sem expor a API ao browser em dev).

## Administrador inicial

Na primeira subida sem nenhum administrador, a API cria
`admin@deladopara.local` com senha aleatória e a registra **uma única vez**
no log de inicialização (`Admin inicial criado`). Nenhuma senha fica no
repositório. Para regerar, apague a conta no banco local e reinicie.

## Solução de problemas

- Health `starting` por >2 min: `docker compose --profile local logs <serviço>`.
- Conflito de porta: ajuste `*_PORT` no `.env`.
- Kafka sem eleger: confira `CLUSTER_ID`/`KAFKA_CONTROLLER_QUORUM_VOTERS` no
  `compose.yml`; single-node exige os fatores de replicação = 1 (já fixados).
