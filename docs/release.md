# Imagens de release — De Lá do Pará

Empacotamento da API, do worker e do frontend SSR (C92). As imagens são
construídas localmente a partir do commit; nada é publicado em registro nem
implantado em nuvem. O workflow de entrega, checksums e SBOM pertencem à C92a.

## Imagens

| Imagem | Dockerfile | Conteúdo |
|---|---|---|
| `de-la-do-para/backend:<versão>` | `infra/release/backend.Dockerfile` | JAR Spring Boot único. Sem `SPRING_PROFILES_ACTIVE` executa a **API**; com `worker` executa o **worker** (outbox Kafka e pagamentos). |
| `de-la-do-para/frontend:<versão>` | `infra/release/frontend.Dockerfile` | Bundle Angular SSR executado por Node, sem `node_modules` nem gerenciador de pacotes. |

API e worker usam a **mesma imagem** e, portanto, o mesmo versionamento. Cada
imagem registra `org.opencontainers.image.version` e `...revision` (SHA do
commit). Bases fixadas por tag e digest: Temurin `25.0.4_7` (JDK no build, JRE
no runtime) e Node `22.23.2`, as mesmas versões do CI. A atualização dos
digests é manual por enquanto.

## Construir e verificar

```bash
scripts/release-smoke.sh                   # constrói as duas imagens e verifica
DLP_RELEASE_VERSION=0.1.0 scripts/release-smoke.sh
DLP_SKIP_BUILD=1 scripts/release-smoke.sh  # reaproveita imagens já construídas
```

O contexto de build é a raiz do repositório; cada Dockerfile tem uma lista de
permissão (`*.Dockerfile.dockerignore`), então `.env`, `tasks/`, testes e
diagnósticos de crash nunca entram no contexto. O JAR usa o timestamp do commit
(`project.build.outputTimestamp`): dois builds sem cache do mesmo commit geraram
o mesmo SHA-256.

O smoke sobe PostgreSQL, Kafka, API, worker e frontend em uma rede Docker
privada (sem portas no host, nomes `dlp-release-smoke-<pid>-*`) e confere:

- usuário diferente de root, `HEALTHCHECK`, versão e commit nos rótulos;
- nenhuma variável de ambiente sensível, ferramenta de desenvolvimento
  (`mvn`, `npm`, `git`, `javac`, ...) nem arquivo `.env`/`.pem`/`.key` em `/app`;
- API saudável com migrations aplicadas no banco vazio, worker saudável e
  frontend saudável;
- API e worker executando a mesma imagem.

Ele **não** executa compra nem fala com sandbox; comportamento continua nos
gates `verify`.

## Executar

Variáveis obrigatórias da API (nada é embutido na imagem):

| Variável | Uso |
|---|---|
| `SPRING_DATASOURCE_URL`, `SPRING_DATASOURCE_USERNAME`, `SPRING_DATASOURCE_PASSWORD` | PostgreSQL. O `application.yml` aponta para `localhost`; em contêiner a URL precisa ser informada. |
| `SHIPPING_PICKUP_ENCRYPTION_KEY` | Chave Base64 de 32 bytes (`openssl rand -base64 32`). Sem padrão. |
| `SPRING_MAIL_HOST`, `SPRING_MAIL_PORT` | SMTP (Mailpit no ambiente local). |

O worker acrescenta `SPRING_PROFILES_ACTIVE=worker` e os parâmetros descritos em
[guia local](local-guide.md#worker-de-outbox) (`APP_EVENTING_*`).

- API e worker escutam em `8080`; a readiness é
  `/actuator/health/readiness` e a liveness `/actuator/health/liveness`.
- O frontend escuta em `4000` (`PORT`). Ele chama a API por caminho relativo
  (`/api/...`), então precisa de um gateway que encaminhe `/api` para a API e o
  restante para o frontend; esse gateway e o compose de release entram na C92a.
- Mídia de produtos fica no volume `/var/lib/deladopara/media`.
- A JVM usa 75% do limite de memória do contêiner e encerra em
  `OutOfMemoryError`; `JAVA_TOOL_OPTIONS` pode ajustar.

## Limites conhecidos

- A imagem passou a ser testada em contêiner, mas **sem gateway**: não há ainda
  jornada de compra sobre as imagens (C92a/C95).
- `application.yml` mantém uma senha de desenvolvimento padrão para
  `localhost`; a imagem não a usa se `SPRING_DATASOURCE_*` for informado, mas
  também não impede um start sem essas variáveis.
- O healthcheck do frontend consulta `/`, uma rota pré-renderizada: não prova a
  renderização de rotas dinâmicas, que dependem da API.
- C92 permanece **parcial** no plano: as dependências C63, C73, C73a, C81, C81a,
  C81b e C83 ainda não existem, então as imagens ainda não contêm essas
  funcionalidades.
