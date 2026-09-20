# De Lá do Pará

Loja de produtos paraenses (alimentos sem refrigeração e artesanato), com
procedência, produtores, entrega e retirada. Projeto de portfólio, executado
localmente, sem custo de serviços.

- Backend: Spring Boot (monólito modular, perfis API/worker), PostgreSQL, Kafka.
- Frontend: Angular (vitrine com SSR + áreas privadas).
- Diferencial técnico: checkout confiável com outbox transacional, consumidores
  idempotentes e conciliação de resultado desconhecido.
- Integrações de teste: Asaas Sandbox e Melhor Envio Sandbox (opt-in).

**Estado:** bootstrap documental. O plano mestre autossuficiente está em
[`docs/PLANO-MESTRE.md`](docs/PLANO-MESTRE.md) e o progresso em
[`tasks/progress.md`](tasks/progress.md). A execução segue o backlog do plano
(C00a → C98); nenhum endpoint ou tela existe ainda.

## Começar (quando o bootstrap C06–C08 existir)

```bash
git clone https://github.com/Gaalbu/de-la-do-para.git
cd de-la-do-para
cp .env.example .env
# ambiente determinístico local (alvo futuro, C08):
# docker compose --profile local up --build -d
```

## Regras do repositório

- Nomes e código em inglês; guias e textos da loja em pt-BR.
- Conventional Commits (`feat`, `fix`, `test`, `docs`, `refactor`, `build`, `ci`, `chore`).
- Cada funcionalidade entrega contrato, exemplos, testes e evidência juntos.
- Sem segredos no Git; credenciais de sandbox só em ambiente local opt-in.

Dados de produtos, produtores, preços e origem são fictícios e identificados
como demonstração. Não há atendimento presencial nem retirada real.
