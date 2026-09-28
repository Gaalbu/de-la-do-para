# Spec de notificações comerciais — C74

## 0. Metadados

- Módulo: `notifications`
- Status: base documental para mensagens comerciais locais; eventos logísticos dependem da definição de C70–C73
- Decisões base: D07, D22–D24, D31, D33–D34, D64–D66, D68
- Dependências: `orders`, `payments`, `shipping`, `eventing`; SMTP local por C08/Mailpit
- Limite: este documento não define verificação ou recuperação de conta, que pertencem ao adapter de `identity` (C76/C77).

## 1. Objetivo e garantias

Enviar avisos transacionais úteis sobre o pedido e a cobrança, sem incluir
segredos, dados de pagamento ou detalhes pessoais desnecessários. Kafka e SMTP
podem redeliver; a aplicação deve deduplicar seu próprio efeito quando for
possível, sem prometer entrega única pelo servidor de e-mail.

O e-mail é um canal de conveniência. Pedido, pagamento, estoque, cupom e
transições não podem depender da disponibilidade SMTP. Uma falha de envio deve
ser repetível e observável; não deve desfazer o fato comercial que originou a
mensagem.

## 2. Destinatário e conteúdo

- O destinatário vem do e-mail de contato imutável do pedido. Igualdade de
  e-mail não associa compra convidada a uma conta (IDN-011).
- O endereço de destino, token `X-Order-Token`, chave de idempotência, link de
  pagamento e payload bruto de webhook nunca entram em log ou no assunto.
- Mensagens usam remetente local `no-reply@deladopara.local`, identificam os
  dados como demonstração e usam texto simples/HTML acessível. Nenhum produtor,
  ponto ou atendimento real pode ser alegado.
- Links não carregam token em query string. Para pedido convidado, a mensagem
  inicial de aceite pode conter uma URL dedicada com o token somente no
  fragmento, sem enviar esse fragmento ao servidor HTTP; a UI troca o token por
  autorização do cabeçalho `X-Order-Token`. Até C75 provar esse fluxo, não
  enviar o link de acesso.

## 3. Matriz evento → mensagem

| Evento | Regra | Conteúdo mínimo | Estado |
|---|---|---|---|
| `order.created` | Uma mensagem de aceite por pedido novo; replay de compra idempotente não cria novo aviso | identificador público do pedido, total BRL, modalidade, forma de consultar o pedido | C75; link de convidado bloqueado até provar transmissão segura do token |
| `order.status_changed` para `PAID` | Avisar confirmação; status do pedido é fonte de verdade, não redirect nem callback de pagamento | identificador do pedido, estado confirmado e próximo passo | C75 |
| `order.status_changed` para `PREPARING`, `READY_FOR_PICKUP`, `IN_TRANSIT`, `DELIVERED`, `PICKED_UP` | Avisar somente quando a transição correspondente for persistida; retirada usa o ponto fictício aprovado e horário D23 | novo status, modalidade e instrução aprovada; detalhes logísticos só quando C70–C73 os definirem | parcial; fonte de evento existe em C50/C51, conteúdo de expedição aguarda C70–C73 |
| `order.status_changed` para `CANCELLED`, `EXPIRED`, `UNDER_REVIEW` | Mensagem neutra de estado; não chamar `REFUND_REQUESTED` de reembolso concluído | novo estado e orientação para acompanhar/contatar a operação local | C75; detalhe financeiro aguarda C67–C69 |
| `payment.status_changed` | Não enviar notificação comercial direta: pedido recebe a decisão final de C61 e produz `order.status_changed`; eventos PAYMENT podem incluir mudanças intermediárias e `UNKNOWN` | — | excluído para evitar mensagem contraditória ou duplicada |
| `payment.refund_requested` / `payment.refunded` | Só notificar quando o estado do pedido/coordenador e o fato de pagamento permitirem linguagem precisa; solicitado e confirmado são estados distintos | valor total em BRL e estado literal da compensação, sem dados de cartão/Pix | depende de C67/C68 |

Usar `(eventId, templateVersion, recipient)` como chave de deduplicação local.
Renderizar eventos de pedido em ordem `aggregateVersion`; se houver lacuna,
reter a mensagem posterior até reconciliar a sequência. Não reconstruir conteúdo
financeiro a partir de payload bruto quando houver consulta autorizada disponível.

## 4. Retentativa e retenção

- O consumo Kafka segue C45: efeito local/recibo no mesmo commit e offset
  depois do commit; redelivery do mesmo `eventId` não cria nova tentativa lógica.
- O envio SMTP ocorre fora da transação de consumo. Persistir uma tentativa
  durável antes da chamada, com chave idempotente local, estado, contagem,
  próximos horários, destinatário e erro sanitizado. Depois do ACK SMTP marcar
  como aceito pelo servidor; isso não prova leitura nem entrega na caixa final.
- Retry transitório usa os valores aprovados em C45: até 8 tentativas, atraso
  exponencial com full jitter, de 1 segundo a 1 minuto. Falha inválida não
  repete; encaminhar para análise/quarentena conforme C49. Falha permanente de
  SMTP não bloqueia o pedido nem o consumer de status.
- Não há retentativa automática ilimitada nem envio de tentativa incerta que
  possa duplicar link de acesso sem idempotência. Mensagem incerta permanece
  auditável e não se declara entregue.
- Guardar o mínimo necessário para recuperar a tentativa. Segredos, token de
  pedido em claro e corpo integral de e-mail não entram no log operacional.
  Retenção de dados do pedido segue ORD-Q01; a fila local de tentativas não
  amplia a retenção pessoal.

## 5. Testes e limites

- Testar evento válido/duplicado, versões fora de ordem, retry transitório,
  SMTP indisponível, falha depois do aceite SMTP, destinatário derivado do
  pedido e conteúdo sem token/PII desnecessária.
- Testar que pagamento confirmado e pedido permanecem corretos mesmo quando
  SMTP está indisponível.
- Inspecionar e-mails locais no Mailpit; isso comprova somente o ambiente local.
- C04 não homologa SMTP externo, Asaas, Melhor Envio nem recebimento na caixa
  final. Nenhuma evidência do Mailpit deve ser descrita como entrega real.

## 6. Perguntas reservadas

- Link de acesso convidado: CHK-Q02 define que o replay não repete o segredo,
  mas não especifica transporte seguro do token bruto. ORD-Q02 define somente
  expiração. O fragmento evita envio ao servidor no pedido HTTP inicial, porém
  aparece no histórico/telemetria do navegador e sua retenção não está coberta
  pelas decisões existentes. Manter bloqueado até decisão ou canal de troca
  seguro para C75/C78.
- Conteúdo exato para pedido em análise, reembolso parcial ou disputa fica
  bloqueado até decisões das specs de checkout/pagamentos; esses casos não
  podem ser apresentados como reembolso concluído.
- Mensagem de despacho, tracking, tentativas de entrega, ponto e código de
  retirada dependem do modelo logístico de C70–C73. O código de retirada não é
  definido aqui nem exposto por e-mail até existir regra de emissão e validação.

## 7. Critérios documentais

| ID | Critério | Evidência |
|---|---|---|
| NOT-DOC-1 | Mensagens derivadas de eventos persistidos e deduplicáveis | matriz §3 vs `SPEC-orders`, `SPEC-payments` e C45 |
| NOT-DOC-2 | E-mail não comanda estado comercial nem promete entrega de caixa | §1/§4 |
| NOT-DOC-3 | Sem segredo ou payload pessoal excessivo em evento, URL ou log | §2/§4; C75 testa |
| NOT-DOC-4 | Conteúdo logístico fica vinculado às decisões de C70–C73 | §3/§6 |

## 8. Verificação

```bash
npm run docs:check --prefix frontend
git diff --check
```
