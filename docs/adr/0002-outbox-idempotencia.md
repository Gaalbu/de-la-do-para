# ADR-0002: outbox transacional e consumidores idempotentes

- Data: 20/09/2026
- Status: aceito (princípio A03/D06); implementação em C45–C49
- Decisores: usuário (D06) + executor

## Problema

Commit no banco com broker fora não pode virar erro genérico nem perder o
evento; duplicatas de transporte não podem repetir efeito comercial; sem
promessa de exactly-once global.

## Alternativas consideradas

| Alternativa | Prós | Contras / custo operacional |
|---|---|---|
| Publicar direto no request | simples | perde evento em queda; acopla HTTP a Kafka |
| Transação XA Kafka+PG | atomicidade | não abrange HTTP/Asaas; complexidade; sem exactly-once fim a fim |
| **Outbox + claim/lease + registro de consumo (escolhida)** | negócio e intenção publicados juntos; redelivery seguro; replay auditado | retenção operacional (proposta 30 dias + identidades financeiras duradouras); operador precisa de UI (C82) |

## Decisão

Outbox explícita na transação do efeito local; publicador marca só após ACK
com claim/lease recuperável; handler + eventId únicos com efeito e registro
no mesmo commit; retry com backoff/jitter/limite; inválidos em quarentena
com replay auditado; UNKNOWN via conciliação, sem reenvio cego. Spring
Modulith usado **só** para fronteiras — sem registry de eventos concorrente.

## Consequências

Máquina de estados e reconciliação absorvem os limites dos provedores
(C04): `externalReference` sem unicidade provada, `minutesToExpire` finito,
UNKNOWN permanente possível → análise administrativa.

## Evidência

Plano §5; `docs/integrations/asaas.md` (at-least-once oficial, persist-then-
200); matriz V01–V22; testes BI em C46–C49/C88.
