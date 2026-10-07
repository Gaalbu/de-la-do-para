# Estudo de caso: um checkout que não cobra duas vezes

Este documento explica as decisões de confiabilidade do checkout e aponta, para
cada uma, o código que a implementa e o teste que a exercita. O que ainda não
existe está em [Limites conhecidos](#limites-conhecidos).

## O problema

Uma compra mexe em quatro lugares ao mesmo tempo: o banco (pedido), o estoque, o
cupom e um provedor de pagamento externo, alcançado por HTTP. Três falhas são
previsíveis:

1. O comprador clica duas vezes, ou o cliente repete a requisição: duas ordens e
   dois estoques reservados.
2. O processo cai no meio: pedido criado sem reserva, ou cobrança pedida sem pedido.
3. O provedor recebe o pedido de cobrança, mas a resposta se perde (timeout).
   Repetir pode cobrar duas vezes; não repetir pode deixar uma cobrança sem dono.

## 1. A mesma compra gera uma única ordem

O cliente envia uma chave de idempotência por compra (16 a 160 caracteres ASCII
imprimíveis, sem espaço). `CheckoutIdempotency.claim` insere a chave em `checkout_idempotency`
com `ON CONFLICT DO NOTHING`, dentro da transação de aceitação:

- inserção nova: a compra segue;
- chave já concluída com o mesmo conteúdo: devolve a ordem existente (replay);
- chave ainda em andamento: `PurchaseInProgressException`;
- mesma chave com conteúdo diferente: `IdempotencyKeyReusedException`. O conteúdo
  é um SHA-256 do snapshot, da versão do carrinho, da entrega, do e-mail, do cupom
  e da versão do resumo.

Como a claim vive na mesma transação, um rollback a remove e o comprador pode tentar
de novo.

- Código: [`CheckoutIdempotency`](../backend/src/main/java/br/com/deladopara/checkout/application/CheckoutIdempotency.java)
- Testes: [`CheckoutIdempotencyIT`](../backend/src/test/java/br/com/deladopara/checkout/application/CheckoutIdempotencyIT.java)
  (`concurrentClaimsOfOneKeyProduceOneOrder`, `rollbackRemovesTheClaimSoTheBuyerCanRetry`) e
  [`PurchaseAcceptanceIT`](../backend/src/test/java/br/com/deladopara/checkout/adapter/web/PurchaseAcceptanceIT.java)
  (`replayReturnsTheSameOrderWithoutTokenAndChangedBodyConflicts`)

## 2. Aceitar a compra é tudo ou nada

`PurchaseAcceptanceService.accept` roda em uma única transação PostgreSQL: claim de
idempotência, conferência do resumo, pedido, reserva de estoque, reserva de cupom,
token de acesso de convidado, intenção de pagamento e consumo do carrinho. Se o
preço mudou desde o resumo, a compra é recusada com o resumo novo e nada é gravado.
Nenhuma chamada HTTP nem envio ao broker acontece nessa transação.

- Código: [`PurchaseAcceptanceService`](../backend/src/main/java/br/com/deladopara/checkout/application/PurchaseAcceptanceService.java)
- Testes: `PurchaseAcceptanceIT` (`guestPurchaseCreatesOrderReservationIntentAndEventsInOneTransaction`,
  `insufficientStockRollsEverythingBack`, `changedPriceAfterSummaryIsRejectedWithTheNewSummaryAndNoWrites`)

## 3. O estoque nunca passa do disponível

`StockReservationService.reserve` segura o estoque por 15 minutos, todos os SKUs ou
nenhum. Os lotes são travados com `SELECT ... FOR UPDATE` em ordem estável
(SKU, lote) para evitar deadlock e alocados primeiro-a-vencer-primeiro-a-sair.
`commit` e `release` são idempotentes e nunca repetem movimentos de estoque.

- Código: [`StockReservationService`](../backend/src/main/java/br/com/deladopara/inventory/application/StockReservationService.java)
- Testes: [`StockReservationIT`](../backend/src/test/java/br/com/deladopara/inventory/application/StockReservationIT.java)
  (`twoBuyersForTheLastUnitGetExactlyOneReservation`,
  `missingUnitOfAnySkuLeavesNoPartialReservation`, `replayCommitAndReleaseNeverRepeatMovements`,
  `commitIsRejectedAtTheExactExpiryInstant`)

## 4. A intenção de cobrar é gravada antes de qualquer chamada externa

`PaymentIntentService.request` grava a intenção de pagamento, a operação
`CREATE_CHECKOUT` pendente e o evento `payment.checkout_requested` (outbox
transacional) na transação de quem chama. É idempotente por pedido. Se a transação
do chamador desfaz, os três desaparecem juntos. Um publicador separado
(`OutboxPublisher`, com claim por lease) envia os eventos ao Kafka.

- Código: [`PaymentIntentService`](../backend/src/main/java/br/com/deladopara/payments/application/PaymentIntentService.java)
- Testes: [`PaymentIntentIT`](../backend/src/test/java/br/com/deladopara/payments/application/PaymentIntentIT.java)
  (`requestWritesIntentPendingOperationAndEventAndIsIdempotentByOrder`,
  `rollbackOfTheCallerDiscardsIntentOperationAndEvent`, `concurrentRequestsForTheSameOrderCreateOneIntent`)

## 5. A chamada ao provedor acontece fora de transação, com lease

`CheckoutOperationRunner` executa três passos:

1. uma transação curta marca a operação como em andamento, com lease de 60 s
   (`payments.worker.lease`), e confirma;
2. o provedor é chamado sem nenhuma transação aberta;
3. outra transação curta grava o resultado: criado, rejeitado ou desconhecido.

Dois workers concorrentes não chamam o provedor duas vezes pela mesma operação. Um
lease vencido nunca autoriza uma nova chamada: a operação vira `UNKNOWN`. Um timeout
depois do envio também vira `UNKNOWN` e não é repetido sozinho, porque a requisição
pode ter chegado ao provedor.

- Código: [`CheckoutOperations`](../backend/src/main/java/br/com/deladopara/payments/application/CheckoutOperations.java)
  e [`CheckoutOperationRunner`](../backend/src/main/java/br/com/deladopara/payments/application/CheckoutOperationRunner.java)
- Testes: [`PaymentWorkerIT`](../backend/src/test/java/br/com/deladopara/payments/infrastructure/PaymentWorkerIT.java)
  (`duplicatedWorkersCallTheProviderOncePerOperation`) e
  [`CheckoutOperationRunnerIT`](../backend/src/test/java/br/com/deladopara/payments/application/CheckoutOperationRunnerIT.java)
  (`timeoutAfterSendingKeepsUnknownAndIsNeverRetried`, `expiredLeaseBecomesUnknownInsteadOfBeingClaimedAgain`)

## 6. Um webhook sozinho não confirma pagamento

`AsaasWebhookController` compara o token em tempo constante, rejeita corpo grande
demais e grava a notificação em um inbox (`ProviderEventInbox`, com
`ON CONFLICT (provider, event_id) DO NOTHING`, só identificadores e estados, sem dados
do cliente) antes de responder 2xx. Depois, `ProviderEventProcessor` consulta o
provedor fora da transação e só confirma o pagamento se o estado for `PAID` e o valor
for igual ao da intenção. Valor diferente leva a `UNDER_REVIEW`.

- Código: [`AsaasWebhookController`](../backend/src/main/java/br/com/deladopara/payments/adapter/web/AsaasWebhookController.java)
  e [`ProviderEventProcessor`](../backend/src/main/java/br/com/deladopara/payments/application/ProviderEventProcessor.java)
- Testes: [`AsaasWebhookIT`](../backend/src/test/java/br/com/deladopara/payments/adapter/web/AsaasWebhookIT.java)
  (`redeliveryIsAcknowledgedAndStoredOnce`, `missingOrWrongTokenIsRejectedAndNothingStored`,
  `databaseFailureIsNeverAcknowledged`) e
  [`PaymentOutcomeIT`](../backend/src/test/java/br/com/deladopara/checkout/application/PaymentOutcomeIT.java)
  (`duplicatedWebhookIsProcessedOnce`)

## 7. Consumidores idempotentes e ordenados por agregado

`EventConsumptionService.consume` mantém, para cada par (handler, agregado), um
cursor com a última versão aplicada. Uma versão já aplicada é `DUPLICATE`, mesmo com
outro `eventId`. Uma versão futura fica guardada como `PENDING_ORDER` e é aplicada em
ordem quando a anterior chega. O efeito, o recibo e o avanço do cursor acontecem na
mesma transação.

- Código: [`EventConsumptionService`](../backend/src/main/java/br/com/deladopara/eventing/application/EventConsumptionService.java)
- Testes: [`EventConsumptionServiceIT`](../backend/src/test/java/br/com/deladopara/eventing/application/EventConsumptionServiceIT.java)
  (`returnsDuplicateForAnAlreadyAppliedAggregateVersionWithDifferentEventId`) e
  [`EventingWorkerConfigIT`](../backend/src/test/java/br/com/deladopara/eventing/infrastructure/EventingWorkerConfigIT.java)
  (`realKafkaDeliveryCommitsAfterPostgresEffectAndReceiptAndReplayIsIdempotent`)

## Limites conhecidos

- O provedor de pagamento é simulado (`SimulatedPaymentProvider`). O adapter do
  Asaas Sandbox (C63) ainda não existe; o webhook do Asaas já está implementado e
  testado.
- A consulta e a correlação de pagamento (C64) e o conciliador automático de
  operações `UNKNOWN` (C65) ainda não existem. Hoje uma operação `UNKNOWN` fica
  registrada e não é repetida; resolvê-la sozinho é trabalho planejado em
  [docs/PLANO-MESTRE.md](PLANO-MESTRE.md).
- Tudo roda localmente com dados de demonstração e nenhum dinheiro real.

## Como verificar

A CI executa os testes de integração do backend contra PostgreSQL e Kafka reais em
cada pull request. Localmente, com Docker ativo:

```bash
scripts/verify.sh backend
scripts/verify.sh docs
```

Os gates e suas limitações estão em [docs/ci.md](ci.md).
