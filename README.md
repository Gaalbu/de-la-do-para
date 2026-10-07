# De Lá do Pará

Loja de produtos paraenses (alimentos sem refrigeração e artesanato), com
procedência, produtores, entrega e retirada. Projeto de portfólio, executado
localmente, sem custo de serviços.

- Backend: Spring Boot (monólito modular, perfis API/worker), PostgreSQL, Kafka.
- Frontend: Angular (vitrine com SSR + áreas privadas).
- Diferencial técnico: checkout confiável com outbox transacional, consumidores
  idempotentes e conciliação de resultado desconhecido.
- Integrações de teste: Asaas Sandbox e Melhor Envio Sandbox (opt-in).

**Estado:** em desenvolvimento ativo. O backend já cobre catálogo, carrinho,
contas, cupons, estoque por lote, aceitação de compra, pedidos com retirada,
intenção de pagamento (provedor simulado) e webhook autenticado do Asaas. A CI
roda mais de 280 testes de backend contra PostgreSQL e Kafka reais, além de
contratos, navegador, segurança e política de commits. O adapter do Asaas Sandbox
e a conciliação automática de pagamentos desconhecidos ainda não existem. As
decisões de confiabilidade do checkout, com código e testes, estão no
[estudo de caso](docs/checkout-case-study.md). Plano em
[docs/PLANO-MESTRE.md](docs/PLANO-MESTRE.md) e evidências em
[tasks/progress.md](tasks/progress.md).

## Começar

```bash
cd /home/gaalbu/codigos
git clone https://github.com/Gaalbu/de-la-do-para.git
cd de-la-do-para
# Somente se ainda não existir; preserve sua configuração:
test -f .env || cp .env.example .env
# Ajuste a senha local antes de subir os serviços.
docker compose --profile local up -d
npm ci --prefix frontend
```

Requisitos: JDK 25 (Temurin 25.0.4 usado na CI), Node 22.23.2, npm 12.0.2 e
Docker. A infraestrutura usa portas próprias: [guia local](docs/local-guide.md).
API e Angular são executados separadamente neste estágio:

```bash
./backend/mvnw -f backend/pom.xml spring-boot:run -Dspring-boot.run.arguments=--server.port=18080
# Em outro terminal:
npm --prefix frontend start
```

A API real oferece `/actuator/health`, `/actuator/health/liveness` e
`/actuator/health/readiness`. `/api/v1/status` é apenas um stub WireMock.
Os endpoints de negócio ficam em `/api/v1`. O proxy Angular encaminha `/api` para
`http://localhost:8080`; como o comando acima sobe a API em 18080, ajuste
`frontend/proxy.conf.json` ou suba a API com `--server.port=8080`.

## Verificar

```bash
scripts/verify.sh backend
scripts/verify.sh contracts
scripts/verify.sh frontend
```

- [CI e requisitos](docs/ci.md): gates reproduzíveis, relatórios e limitações.
- [API](docs/api-guide.md): contrato canônico e exemplos executáveis.
- [Estudo de caso do checkout](docs/checkout-case-study.md): idempotência, outbox e resultado desconhecido.
- [Observabilidade](docs/observability.md): correlação, logs e probes.
- [Imagens de release](docs/release.md): API, worker e frontend em imagens imutáveis.
- [Backup e restauração](docs/backup-restore.md): migrations com dados, cópia do banco e conferência em banco separado.

## Regras do repositório

- Nomes e código em inglês; guias e textos da loja em pt-BR.
- Conventional Commits (`feat`, `fix`, `test`, `docs`, `refactor`, `build`, `ci`, `chore`).
- Cada funcionalidade entrega contrato, exemplos, testes e evidência juntos.
- Sem segredos no Git; credenciais de sandbox só em ambiente local opt-in.

Dados de produtos, produtores, preços e origem são fictícios e identificados
como demonstração. Não há atendimento presencial nem retirada real.
