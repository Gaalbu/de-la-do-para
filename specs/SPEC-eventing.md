# Spec de eventing — C45

## 0. Metadados

- Módulo: `eventing`
- Status: em revisão; não autoriza implementação de C46–C49
- Decisões base: D06, D40, D61, D63 e ADR-0002
- Dependências: C04 permanece sem homologação real dos provedores; suas
  limitações são tratadas como incertezas, não como garantias.

## 1. Objetivo

Definir o contrato e as garantias observáveis para transportar eventos
internos entre a transação PostgreSQL, a outbox e os consumidores Kafka. A
especificação protege a consistência local, permite redelivery seguro e deixa
replay/quarentena auditáveis sem prometer entrega global `exactly-once`.

Ficam fora desta etapa a criação da tabela outbox (C46), o publicador (C47),
os consumidores (C48), retry/quarentena executáveis (C49), eventos comerciais
definitivos de `orders`/`payments` e qualquer chamada real aos sandboxes.

## 2. Base técnica e fontes

Esta spec usa as seguintes garantias verificadas:

- O Kafka documenta que a entrega padrão é `at-least-once`: um processo pode
  repetir o processamento quando falha depois do efeito e antes do commit do
  offset. [Kafka Design — Message Delivery Semantics](https://kafka.apache.org/40/design/design/)
- O consumidor deve confirmar o offset somente depois que o processamento
  correspondente estiver concluído; commit anterior pode perder registros.
  [KafkaConsumer 4.2 — commitSync](https://kafka.apache.org/42/javadoc/org/apache/kafka/clients/consumer/KafkaConsumer.html)
- A outbox grava a mensagem na mesma transação do efeito local e um processo
  separado publica depois; o relay pode publicar duplicatas, portanto o
  consumidor precisa ser idempotente. [Transactional Outbox](https://microservices.io/patterns/data/transactional-outbox)

Essas fontes descrevem o broker e o padrão de integração; não provam a
homologação Asaas/Melhor Envio de C04.

## 3. Envelope canônico

O contrato canônico é
[`contracts/events/envelope.schema.json`](../contracts/events/envelope.schema.json),
validado por `contracts:check`. Cada evento contém:

| Campo | Regra |
|---|---|
| `eventId` | UUID estável; identifica o fato e nunca muda em redelivery |
| `eventType` | nome estável do fato, versionado por `schemaVersion` |
| `schemaVersion` | inteiro positivo; mudança incompatível exige nova versão |
| `aggregateId` | identidade opaca do agregado de origem |
| `aggregateVersion` | versão monotônica do agregado, iniciando em zero |
| `occurredAt` | instante UTC em que o efeito local foi confirmado |
| `correlationId` | acompanha a jornada original, sem servir como autorização |
| `causationId` | evento/operação que causou este fato; não usar valor fictício |
| `payload` | objeto específico do tipo, sem segredo, token ou PII desnecessária |

`eventId` é a chave de deduplicação do fato. A deduplicação de efeito é por
`eventId` **e** identidade do handler, pois handlers diferentes podem reagir
ao mesmo evento. O envelope não carrega estado operacional de retry; tentativas,
leases e quarentena pertencem à persistência do eventing.

## 4. Fluxos e garantias

### 4.1 Publicação

1. O módulo de negócio atualiza seu agregado e insere o envelope na outbox na
   mesma transação PostgreSQL.
2. Rollback remove o efeito e o evento juntos; uma transação confirmada deixa
   o evento elegível para publicação.
3. O worker reivindica uma linha com lease recuperável. O claim não significa
   publicação confirmada.
4. O worker publica com a chave Kafka `aggregateId` e só registra o estado
   `PUBLISHED` depois do ACK do broker.
5. Queda depois do ACK e antes do registro pode causar redelivery. O desenho
   deve tolerar a duplicata; não há janela de publicação silenciosamente
   perdida.

### 4.2 Consumo

1. O consumidor valida envelope, versão e compatibilidade antes do efeito.
2. `eventId + handlerName` é uma identidade única no registro de consumo.
3. O efeito de negócio e o registro de consumo são gravados no mesmo commit
   local.
4. O offset Kafka só avança depois desse commit. Falha antes dele deixa o
   registro apto a redelivery.
5. Um evento já registrado para o mesmo handler é um redelivery benigno; o
   handler não repete o efeito.
6. Payload inválido, versão incompatível ou evento impossível não entra em
   loop infinito: vai para quarentena com motivo, correlação e tentativa.

### 4.3 Ordem, versão e replay

- A chave de partição padrão é `aggregateId`; isso preserva a ordem relativa
  dos eventos do mesmo agregado dentro da partição.
- `aggregateVersion` é verificado pelo consumidor. Um gap não é aplicado como
  se estivesse em ordem: fica pendente para retry/reconciliação.
- Replay reutiliza o `eventId` original e registra o operador/motivo em uma
  trilha separada. Não cria um novo fato nem permite bypass da deduplicação.
- Eventos de pagamento, expedição e webhook continuam sujeitos a resultado
  `UNKNOWN`; replay não recria chamada externa sem uma operação de
  conciliação específica.

## 5. Retry, lease, quarentena e retenção

Os valores seguintes são proposta operacional para revisão, não decisão do
usuário:

| Item | Proposta | Limite que deve ser testado |
|---|---|---|
| Lease | duração configurável e renovável somente durante o processamento | queda do worker libera a mensagem sem duplicar efeito |
| Backoff | exponencial com jitter e teto configurável | reinício não cria tempestade de retries |
| Tentativas | limite por tipo de falha, separado entre transitória e inválida | falha inválida vai à quarentena no limite |
| Outbox operacional | retenção inicial proposta de 30 dias após publicação | limpeza não remove evento ainda pendente ou em investigação |
| Identidades financeiras | retenção duradoura conforme auditoria/reconciliação | não apagar referência necessária para UNKNOWN |

Nenhum retry deve manter uma transação HTTP aberta ou chamar provedor externo
sem uma chave/registro de operação próprio. A implementação deve registrar
`lastError` sanitizado, `attemptCount`, timestamps, lease e estado; nunca
segredo, token ou payload pessoal desnecessário.

## 6. Falhas que a implementação deve demonstrar

| ID | Cenário | Resultado exigido |
|---|---|---|
| EVT-001 | rollback do efeito local | nenhum evento publicável |
| EVT-002 | worker cai antes do publish | lease expira e evento volta a elegível |
| EVT-003 | worker cai depois do ACK | redelivery não repete efeito |
| EVT-004 | consumidor cai antes do commit local | offset não avança; redelivery é seguro |
| EVT-005 | consumidor cai depois do efeito e commit | segunda entrega é ignorada pelo registro único |
| EVT-006 | envelope inválido | quarentena com diagnóstico sanitizado |
| EVT-007 | versão incompatível ou gap | não aplicar silenciosamente; aguardar/reconciliar |
| EVT-008 | replay autorizado | mesmo `eventId`, auditoria e deduplicação preservadas |
| EVT-009 | limpeza operacional | pendentes/investigados preservados conforme retenção |

## 7. Contratos e limites

- C45 define o contrato e a evidência esperada; não declara que Kafka ou os
  workers já estão implementados.
- C04 precisa comprovar separadamente as garantias reais de Asaas/Melhor
  Envio. Kafka não transforma uma chamada HTTP externa em operação
  `exactly-once`.
- A ordem entre agregados diferentes não é garantida.
- O eventing não decide preço, cupom, estoque, cancelamento ou reembolso; ele
  transporta fatos e registra efeitos conforme os módulos donos.
- `read_committed` e transações Kafka podem ser avaliados em C47/C48, mas não
  substituem a transação PostgreSQL nem autorizam promessa global de entrega
  única.

## 8. Verificação planejada

- `npm --prefix frontend run docs:check`
- `npm --prefix frontend run contracts:check`
- Validação do schema válido e rejeição do exemplo inválido.
- Revisão humana desta spec e do ADR-0002 antes de C46.
- C46–C49 acrescentarão testes de integração PostgreSQL/Kafka para EVT-001 a
  EVT-009; esta spec não usa mocks como evidência de entrega real.
