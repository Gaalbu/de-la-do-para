# Arquitetura, trade-offs e matriz de falhas

Documento de fechamento da v1 (C88 e C96). Cada afirmação de comportamento
aponta para um teste de integração com PostgreSQL real (Testcontainers) que a
prova. Decisões completas: [decisions.md](decisions.md) e [adr/](adr/).

## O que existe

Monólito modular Spring Boot 4 (Java 25) com dois processos da mesma imagem:
**API** e **worker** (perfil `worker`: publicação da outbox, pagamentos,
conciliação, expiração). PostgreSQL guarda o negócio e a outbox; Kafka leva os
fatos entre módulos; Angular SSR serve vitrine e checkout.

Módulos (`backend/src/main/java/br/com/deladopara`): `catalog`, `inventory`,
`pricing`, `cart`, `shipping` (cotação e retirada), `checkout`, `orders`,
`payments`, `eventing`, `identity`, `observability`. `ArchitectureRulesTest`
impede acesso direto ao repositório de outro módulo.

```
request ─► transação local: efeito + linha na outbox ─► commit
                                   │
        worker: claim/lease ─► Kafka ─► ACK ─► marca publicado
                                   │
        consumidor: efeito + recibo (eventId) na mesma transação
```

## Decisões que valem a leitura

| Decisão | Por quê | Custo assumido |
|---|---|---|
| Outbox na transação do efeito, sem XA ([ADR-0002](adr/0002-outbox-idempotencia.md)) | Commit e intenção de publicar nunca divergem | Entrega *at-least-once*: o consumidor precisa ser idempotente. Não há promessa de *exactly-once* |
| Resposta ambígua do provedor é `UNKNOWN`, nunca falha | Timeout não prova que o pagamento não saiu | Conciliação por consulta com backoff; sem reenvio cego |
| Webhook só confirma se a consulta ao provedor bater (valor e checkout) | Webhook forjado não paga pedido | Uma chamada extra por notificação; falha de consulta mantém a notificação para nova tentativa |
| Reserva de estoque e cupom por constraint e lock curto | Última unidade e limite de cupom sob concorrência | Reserva dura 15 min; expira por worker |
| Pagamento tardio após expiração vira reembolso, não pedido pago | Estoque já foi devolvido | Reembolso é operação durável e conciliada |
| Sessão por cookie + CSRF, convidado por token só com hash ([ADR-0003](adr/0003-sessoes-cookie-csrf.md)) | Sem JWT em storage do navegador | SSR separa páginas públicas das privadas ([ADR-0004](adr/0004-ssr-publico-privado.md)) |
| Provedores simulados por padrão; adapter Asaas opt-in | Custo zero e testes determinísticos | Sem prova em sandbox real nesta versão |

## Matriz de falhas

| Cenário | Comportamento | Prova |
|---|---|---|
| Cliente repete a compra (mesma chave) | Mesmo pedido; corpo diferente com a mesma chave dá conflito | `CheckoutIdempotencyIT`, `PurchaseAcceptanceIT` |
| Falha no meio da aceitação | Nada persiste; a chave não fica presa | `CheckoutIdempotencyIT.rollbackRemovesTheClaimSoTheBuyerCanRetry`, `PurchaseAcceptanceIT.insufficientStockRollsEverythingBack` |
| Preço mudou depois do resumo | Rejeita com o resumo novo, sem gravar | `PurchaseAcceptanceIT.changedPriceAfterSummaryIsRejectedWithTheNewSummaryAndNoWrites` |
| Dois compradores, última unidade | Exatamente uma reserva | `StockReservationIT.twoBuyersForTheLastUnitGetExactlyOneReservation` |
| Limite de cupom sob concorrência | Nunca excede o limite global nem por e-mail | `CouponReservationServiceIT` |
| Kafka cai após o ACK, antes de marcar | Evento é reentregue | `OutboxPublisherPersistenceIT.redeliversAfterWorkerDiesAfterKafkaAckBeforeDatabaseMark` |
| Worker morre com a linha alugada | Lease expira e outro worker assume | `OutboxPublisherPersistenceIT.expiredLeaseMakesTheEventClaimableAgain` |
| Mensagem entregue duas vezes | Efeito e recibo uma vez só | `EventConsumptionServiceIT.appliesEffectAndReceiptOnceAndTreatsRedeliveryAsDuplicate` |
| Handler falha | Efeito e recibo revertem juntos | `EventConsumptionServiceIT.rollsBackEffectAndReceiptWhenHandlerFails` |
| Falha transitória / evento inválido | 8 tentativas com backoff ≤ 1 min, depois quarentena; inválido vai direto, sem payload no diagnóstico | `EventFailureServiceIT` |
| Replay de quarentena | Só admin, com CSRF e motivo; consome o envelope original uma vez | `EventReplayIT` |
| Webhook sem token / corpo grande / banco fora | Rejeitado sem gravar; banco fora nunca é confirmado | `AsaasWebhookIT` |
| Webhook repetido ou atrasado | Guardado uma vez; nunca regride pagamento confirmado | `AsaasWebhookIT`, `PaymentReconciliationIT` |
| Webhook forjado ou de outro checkout | Não confirma; vai para revisão | `PaymentOutcomeIT`, `PaymentReconciliationIT` |
| Valor divergente | Pedido não é marcado pago; revisão | `PaymentOutcomeIT.divergentAmountGoesToReviewWithoutConfirmingTheOrder` |
| Timeout ao criar o checkout (`UNKNOWN`) | Consulta após backoff confirma sem criar de novo; resposta vazia não é prova | `UnknownPaymentRecoveryIT` |
| Consulta ao provedor falha | Reagenda; nunca lida como "não pago" | `PaymentReconciliationIT.lookupFailureIsRetriedAndNeverReadAsNotPaid` |
| Reserva expira | Estoque e cupom devolvidos uma vez; expiração e confirmação concorrentes terminam em estado válido | `CheckoutExpirationIT` |
| Pagamento depois da expiração | Pedido continua expirado; reembolso solicitado | `CheckoutExpirationIT`, `PaymentOutcomeIT` |
| Reembolso com resposta perdida | Resolvido por consulta, nunca reenviado; workers concorrentes enviam uma vez | `RefundLifecycleIT` |
| Migração sobre dados antigos / restauração | Preserva dados, chaves e histórico | `DatabaseUpgradeAndRestoreIT` |
| Sessão e permissões | Sessão rotaciona no login; mutação exige CSRF; cliente não alcança rotas admin | `SessionSecurityIT` |
| Acesso a pedido | Convidado só com o token do pedido (guardado como hash); cliente não vê pedido alheio | `OrderAccessIT` |
| Rota sem contrato / drift de schema | Reprova no build | `RouteContractCoverageTest`, `contracts:check` |

## O que a v1 não faz

Sem prova em sandbox real de pagamento e frete (provedores simulados; o adapter
Asaas é testado com HTTP gravado), sem etiquetas e rastreio, sem notificações
por e-mail, sem UI de conta nem de cupons (API apenas). Ver o corte de escopo em
[PLANO-MESTRE.md](PLANO-MESTRE.md).
