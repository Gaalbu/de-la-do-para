# De Lá do Pará

Loja de produtos paraenses (alimentos sem refrigeração e artesanato), com
procedência, produtores, entrega e retirada. Projeto de portfólio, executado
localmente, sem custo de serviços.

- Backend: Spring Boot (monólito modular, perfis API/worker), PostgreSQL, Kafka.
- Frontend: Angular (vitrine com SSR + áreas privadas).
- Diferencial técnico: checkout confiável com outbox transacional, consumidores
  idempotentes e conciliação de resultado desconhecido.
- Pagamento e frete são **simulados** por padrão (determinísticos, sem custo).
  O adapter Asaas existe, é opt-in (`PAYMENTS_PROVIDER=asaas`) e é testado com
  respostas HTTP gravadas; não há prova em sandbox real nesta versão.

**Estado (v1):** vitrine, carrinho, checkout com reserva de estoque e cupom,
pagamento assíncrono, conciliação, expiração, cancelamento e reembolso, retirada
local, contas por API, painel de operações e observabilidade (métricas, traces e
painéis Grafana) estão em `main`. Fora da v1: etiquetas e rastreio de frete,
notificações por e-mail, UI de conta e de cupons. Arquitetura, trade-offs e a
matriz de falhas provadas por teste: [docs/architecture.md](docs/architecture.md).
Plano: [docs/PLANO-MESTRE.md](docs/PLANO-MESTRE.md); evidências em
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

A API oferece `/actuator/health` (`liveness`, `readiness`) e os endpoints de
negócio descritos em [docs/api-guide.md](docs/api-guide.md). O proxy Angular
`/api` aponta para a porta 8080; ao subir a API em 18080 como acima, ajuste o
proxy.

## Verificar

```bash
scripts/verify.sh backend
scripts/verify.sh contracts
scripts/verify.sh frontend
```

- [Arquitetura e matriz de falhas](docs/architecture.md): decisões, trade-offs e testes que provam cada falha.
- [CI e requisitos](docs/ci.md): gates reproduzíveis, relatórios e limitações.
- [API](docs/api-guide.md): contrato canônico e exemplos executáveis.
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
