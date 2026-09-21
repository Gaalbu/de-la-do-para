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
