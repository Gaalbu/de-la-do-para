# ADR-0001: monólito modular com perfis API e worker

- Data: 20/09/2026
- Status: aceito (D61/D62)
- Decisores: usuário (D61/D62) + executor

## Problema

Demonstrar checkout confiável com falhas e recuperação observáveis, sem
operar microsserviços, cluster ou custo de serviços.

## Alternativas consideradas

| Alternativa | Prós | Contras / custo operacional |
|---|---|---|
| Microsserviços por módulo | deploy independente | rede, versionamento e observabilidade além do escopo; custo e RAM locais |
| Monólito único processo | mais simples | não demonstra queda isolada de worker/broker nem claims concorrentes |
| **Monólito modular, API + worker separados (escolhida)** | fronteiras verificáveis, um artefato, dois processos; PostgreSQL + Kafka reais | exige disciplina de dependências (Modulith/ArchUnit) |

## Decisão

Backend Spring Boot como monólito modular; API e worker executam o mesmo
artefato em perfis/processos separados, compartilhando PostgreSQL e
contratos internos; Angular como aplicação separada. Monorepo com
`backend/`, `frontend/`, `contracts/`, `infra/`, `docs/`, `specs/`, `tasks/`.

## Consequências

Transação PostgreSQL coordena módulos no monólito; nenhuma transação fica
aberta durante HTTP externo ou espera Kafka; dependência só via contrato
público; sem acesso a repository de outro módulo.

## Evidência

D61/D62 em `docs/decisions.md`; mapa em `docs/scope.md`; teste de
arquitetura previsto em C09.
