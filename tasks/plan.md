# Plano de implementação — pricing e inventário

Este plano transforma as specs revisadas em fatias executáveis. Não aprova as
regras marcadas como propostas; qualquer item bloqueado permanece fora do
código até decisão explícita.

## C30 — totais canônicos

**Pré-requisitos:** revisão de `SPEC-pricing.md`; C21 publicado na linha-base.

1. Criar tipos de valor monetário em centavos e moeda, com validação de sinal,
   overflow e moeda diferente de BRL.
2. Implementar cálculo puro de linhas, subtotal, desconto limitado, frete e
   total na ordem documentada; receber snapshot de preços do catálogo e nunca
   confiar em total do navegador.
3. Escrever BT(PurchaseTotal) para os exemplos PRC-002, incluindo 15% sobre
   R$ 18,01, cupom fixo maior que subtotal, mínimo não atingido e moeda inválida.
4. Atualizar contrato/exemplos somente se C30 expuser endpoint; manter cálculo
   interno até existir contrato público autorizado.

**Checkpoint:** testes unitários verdes, `git diff --check`, Spotless/Checkstyle,
contrato sem drift e revisão de arredondamento. Nenhuma reserva ou migration de
cupom entra nesta fatia.

## C31 — reserva atômica de cupom

**Pré-requisitos:** C29 aprovada, C30 implementada e regra global decidida.

1. Modelar uso por cupom e identidade de e-mail verificado, com estados
   reservado/consumido/liberado e referência idempotente.
2. Reservar dentro da transação curta de checkout, com constraint e lock que
   impeçam concorrência acima do limite; não manter transação aberta esperando
   provedor ou Kafka.
3. Confirmar no pagamento autorizado, liberar somente nos estados aprovados e
   preservar histórico após reembolso integral conforme D34.
4. Escrever BI(CouponReservation) para concorrência V11, expiração, falha,
   replay e payload/referência repetidos.

**Bloqueio atual:** combinação de cupons e ciclo do contador global ainda são
propostas em `SPEC-pricing.md`; não codificar a política até a resposta do
usuário e revisão da spec.

## C25/C26 — inventário

`SPEC-inventory.md` define a base documental. C26 pode começar após revisão de
C25: migration de lotes/ledger, constraints de saldo e testes PostgreSQL. A
política FEFO e o tratamento de lote bloqueado com reserva ativa continuam
propostas e devem ser resolvidos antes de alocação em C57.

## Gates por fatia

```bash
npm run docs:check --prefix frontend
git diff --check
npx aislop scan --changes --json
```

Código funcional também executa os gates backend/frontend/contrato aplicáveis
e não considera PR aberta ou mergeável como integração concluída.

## C45–C49 — eventing e recuperação

O contrato e os parâmetros operacionais da `SPEC-eventing.md` foram revisados e
aprovados pelo usuário em 2026-09-24. Os passos abaixo descrevem a ordem de
implementação; aprovação não substitui implementação, evidência dos checkpoints
ou homologação real dos provedores em C04.

### C46 — outbox na transação local

1. Criar a migration da outbox com `eventId` único, tipo/versão, agregado e
   versão, instantes UTC, correlação/causação, payload JSONB, estado,
   `availableAt`, tentativas, lease e último erro sanitizado.
2. Criar o port do eventing e um writer usado pela transação do módulo dono;
   não abrir transação nova nem publicar Kafka durante o request.
3. Testar rollback do efeito e do evento juntos, payload imutável e seleção de
   eventos pendentes sem reivindicar uma linha já alugada.
4. Validar que segredos, tokens e PII desnecessária não entram no payload nem
   no diagnóstico.

**Checkpoint C46:** migration/entidade, teste PostgreSQL e contrato do envelope
passam; ainda não existe publisher nem consumer real.

### C47 — publisher com claim/lease e ACK

1. Implementar worker separado com claim recuperável e limite de lote; lease
   expirado volta a `PENDING` sem apagar a mensagem.
2. Publicar usando `aggregateId` como chave e o envelope como valor; registrar
   `PUBLISHED` somente após ACK do broker.
3. Cobrir queda antes do publish, depois do ACK e antes do registro do ACK;
   o último cenário deve provar redelivery possível, não ausência silenciosa.
4. Medir backlog, idade do evento, tentativas e quarentena sem registrar
   payload sensível nos logs.

**Checkpoint C47:** Testcontainers Kafka real, restart do worker e nenhum
   efeito comercial duplicado; não alegar exactly-once global.

### C48 — consumo idempotente

1. Criar registro único por `eventId + handlerName`, com versão/resultado e
   correlação auditável.
2. Validar envelope e versão antes do handler; aplicar o efeito e registrar o
   consumo no mesmo commit PostgreSQL.
3. Confirmar offset somente depois do commit; redelivery após queda deve ser
   ignorado pelo registro único.
4. Separar eventos fora de ordem: gap de `aggregateVersion` fica pendente e
   não é aplicado como se estivesse atualizado.

**Checkpoint C48:** duplicata, rebalance, queda antes/depois do commit e
   incompatibilidade de schema cobertos com PostgreSQL/Kafka reais.

**Estado atual:** checkpoint C48 verificado localmente em 2026-09-24. Ledger,
handler transacional, validação estrita de envelope, controle de gaps e adapter
Kafka manual estão implementados. Testes com PostgreSQL/Kafka reais cobrem
redelivery após reinício, rebalanceamento, commits posteriores ao efeito local,
deduplicação e incompatibilidade de schema sem avançar o offset. Os tópicos de
entrada e saída são separados. O consumer segue opt-in e desabilitado por
padrão; não há handler comercial habilitado. C49 ainda precisa implementar
retry e quarentena para não deixar falhas permanentes bloquearem a partição.

### C49 — retry, quarentena e replay

1. Classificar falha transitória, inválida e dependência em estado `UNKNOWN`;
   apenas a primeira recebe retry automático.
2. Aplicar backoff/jitter e limite configuráveis; preservar `eventId`, motivo,
   tentativas e timestamps.
3. Mover falha inválida ou excedente para quarentena sem loop infinito e
   permitir replay autorizado com o mesmo `eventId`.
4. Testar retenção sem remover pendências/investigações e sem recriar chamada
   externa depois de resultado desconhecido.

**Checkpoint C49:** cenário de recuperação completo, trilha auditável e
   operador consegue distinguir retry, quarentena, replay e conciliação.

**Situação:** C46 e fatias de C47 já estão integradas, mas C47 permanece
incompleta até os comportamentos aprovados (incluindo renovação de lease e
recuperação operacional) serem implementados/verificados. C48 está liberada
para implementação conforme esta ordem; C49 implementará retry/quarentena,
backoff, limites e retenção aprovados. Garantias dos provedores Asaas/Melhor
Envio continuam dependentes da homologação real do sandbox C04.
