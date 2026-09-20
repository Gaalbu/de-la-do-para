# ADR-0005: perfis de ambiente e túnel de webhooks

- Data: 20/09/2026
- Status: túnel aceito pelo usuário; perfis propostos (implementa em C08)
- Decisores: usuário (túnel) + executor (perfis)

## Problema

Reprodução determinística sem contas externas, homologação sandbox opt-in e
observabilidade/carga opcionais em máquina de ~15 Gi, com portas 5432/6379/
8080 já ocupadas.

## Alternativas consideradas

| Alternativa | Prós | Contras / custo operacional |
|---|---|---|
| Tudo sempre ligado | simples | estoura RAM; conflita portas; mistura teste e demo |
| **Perfis `local` / `sandbox` / `observability` / `load` (escolhida)** | determinístico por padrão; custo sob demanda | Compose com health checks e nomes próprios exigido (C08) |
| ngrok p/ webhooks | estável | exige conta/cadastro |
| **Cloudflare quick tunnel p/ webhooks (escolhida)** | sem cadastro, temporário, rota mínima | URL efêmera a cada subida; fallback ngrok se falhar |

## Decisão

- `local`: PostgreSQL, Kafka KRaft, API, worker, Angular, Mailpit,
  simuladores; portas/volumes próprios; sem contas externas.
- `sandbox`: mesmos contratos + Asaas/Melhor Envio reais via `.env` local
  (credenciais com ajuda posterior, sem Git/CI).
- `observability`/`load`: opcionais (Collector/Prometheus/Grafana; k6).
- Túnel expõe **só** `/api/v1/webhooks/*`; banco/broker/métricas na rede
  local. Orçamento de memória medido em C12/C79.

## Consequências

Gates locais nunca chamam sandbox; homologação manual com relatório
sanitizado; restore antigo pausa efeitos financeiros até reconciliar.

## Evidência

Resposta do usuário 20/09/2026 (túnel aprovado; `.env` depois);
`docs/integrations/homologation.md`; ambiente C00a em `tasks/progress.md`.
