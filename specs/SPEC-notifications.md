# Spec de notificações comerciais — C74

## 0. Metadados

- Módulo: `notifications`
- Status: proposta para revisão; sem implementação
- Dependências: `SPEC-eventing` (C45), `SPEC-orders` (C50), `SPEC-payments` (C53), contratos de checkout (C41) e Mailpit local (C08)
- Decisões relacionadas: D33–D34, D64, D66 e D71
- Implementação planejada: C75, depois de C71 e C72

## 1. Objetivo e limites

Enviar ao contato da compra mensagens transacionais necessárias para concluir
o pagamento e acompanhar o pedido, inclusive como convidado. A aplicação
registra e tenta entregar cada mensagem de forma durável e observável, mas não
controla a aceitação, entrega na caixa postal, leitura ou ausência de spam no
servidor destinatário.

Esta spec cobre notificações comerciais de pedidos e expedição. Verificação e
recuperação de conta pertencem ao adapter SMTP de `identity`; `identity` não
importa `notifications`, `orders` ou `payments`. A confirmação financeira e o
estado do pedido continuam sendo fatos dos módulos `payments` e `orders`, não
do e-mail. Nenhum fluxo de compra ou pagamento aguarda SMTP.

## 2. Princípios e garantias

1. **Obrigação local, efeito externo incerto.** A intenção de notificar é
   durável e recuperável. SMTP pode aceitar a mensagem e a conexão cair antes
   da resposta; nesse caso uma nova tentativa pode gerar duplicata. O sistema
   não promete entrega `exactly-once` nem que uma mensagem aceita chegue ou
   seja lida.
2. **Origem dos dados.** Mensagens são derivadas de fatos confirmados e dados
   autorizados do pedido. Payloads de eventos permanecem sem e-mail, endereço,
   token ou código de retirada, conforme `SPEC-orders` e `SPEC-eventing`.
3. **Mínimo necessário.** Incluir apenas informação que permita reconhecer o
   evento e seguir para a página segura do pedido. Endereço completo,
   credenciais, dados de pagamento, token de acesso e código de retirada não
   aparecem no assunto, corpo, logs nem métricas.
4. **Independência.** Indisponibilidade, lentidão ou rejeição SMTP não desfaz
   pedido, pagamento, estoque, expedição ou confirmação de retirada.
5. **Conteúdo demonstrativo.** Mensagens e links deixam claro o caráter de
   demonstração do produto e não sugerem que uma entrega, retirada física ou
   pagamento real ocorreu fora do estado confirmado pelo sistema.

## 3. Matriz proposta evento → mensagem

Somente eventos listados abaixo podem originar mensagens nesta versão. Um
evento fora da matriz não envia e-mail por inferência.

| Fato confirmado | Destinatário | Conteúdo mínimo proposto | Não incluir |
|---|---|---|---|
| `payment.checkout_available` | Contato de compra do pedido | Identificação curta do pedido, total/moeda e link hospedado de pagamento com validade, se ainda utilizável | Dados de cartão, Pix copia-e-cola fora do link do provedor, segredo ou detalhe técnico |
| `payment.status_changed` → `CONFIRMED` e pedido efetivamente `PAID` | Contato de compra | Pedido, valor confirmado e link para acompanhamento | Dizer “pago” baseado somente em `UNKNOWN`, webhook não conciliado ou divergência |
| `payment.status_changed` → `DECLINED` | Contato de compra | Estado não aprovado e link para retomar/consultar, somente se ainda existir ação segura | Motivo sensível ou garantia de nova tentativa quando não autorizada |
| `order.status_changed` → `PREPARING` | Contato de compra | Atualização curta e link de acompanhamento | Endereço ou snapshot completo |
| `order.status_changed` → `IN_TRANSIT` | Contato de compra | Atualização e link de acompanhamento; rastreio somente quando existir identificador/URL confirmado | Promessa de prazo não confirmada pela integração |
| `order.status_changed` → `READY_FOR_PICKUP` | Contato de compra | Aviso de que o pedido está pronto, ponto/prazo conforme página segura e link de acompanhamento | Código de retirada; D71 determina que ele apareça apenas na tela segura do pedido |
| `order.status_changed` → `DELIVERED` ou `PICKED_UP` | Contato de compra | Estado final confirmado e link de acompanhamento | Afirmar entrega total antes de todos os pacotes ou retirada antes da confirmação administrativa |
| `order.status_changed` → `CANCELLED` | Contato de compra | Cancelamento registrado e link para consultar reembolso, se houver | Afirmar reembolso concluído enquanto pagamento estiver `REFUND_REQUESTED`/pendente |
| `payment.refund_requested` / `payment.refunded` | Contato de compra | Solicitação de reembolso ou reembolso confirmado, conforme o fato correspondente | Colapsar solicitado e confirmado num só estado |
| `order.status_changed` → `UNDER_REVIEW` | Contato de compra | Pedido requer análise e link seguro para acompanhamento/contato | Causa interna, dados de risco ou promessa de decisão/prazo |

**Itens propostos sujeitos à revisão:** enviar atualização de `PREPARING`,
`DECLINED` e `UNDER_REVIEW`; enviar confirmação de `CANCELLED` separada de
reembolso; e enviar conclusão para cada pacote versus um resumo por pedido.
Não enviar mensagens para toda mudança de estado por padrão: estados
intermediários, retries, reconciliações sem mudança visível e eventos
duplicados não devem criar ruído.

O token/link de convidado é emitido e validado pelo dono de `orders`; seu
transporte por e-mail precisa cumprir a política de validade de
`SPEC-orders` ORD-Q02. Nenhum token é colocado em logs, telemetria ou evento.
Se o link precisar ser repetível ou reemitido, isso deve usar um fluxo seguro
de emissão pelo módulo dono, e não armazenar o token em claro no registro de
notificação.

## 4. Idempotência, retry e estados

- A identidade lógica de uma mensagem é `(eventId, notificationType,
  recipientPurpose)`. A mesma entrega de evento ao mesmo handler não cria uma
  segunda intenção. Dois fatos legítimos distintos podem gerar mensagens
  distintas mesmo que tenham conteúdo parecido.
- Persistir intenção e estado antes de enviar SMTP; o registro deve permitir
  recuperação após reinício e inspeção de resultado incerto. A mensagem não
  deve manter transação de negócio ou HTTP aberta durante chamada SMTP.
- Estados mínimos propostos: `PENDING`, `SENDING`, `SENT_TO_SMTP`, `RETRY`,
  `PERMANENT_FAILURE`, `UNKNOWN` e `SUPPRESSED`. `SENT_TO_SMTP` significa
  somente que o servidor SMTP local/remoto aceitou a mensagem, não entrega ao
  destinatário.
- Falhas claramente transitórias (conexão recusada, timeout antes de enviar,
  resposta SMTP temporária) podem ser tentadas novamente com backoff e limite
  configurados. Erros permanentes (destinatário rejeitado, remetente inválido,
  configuração inválida) não devem repetir indefinidamente; registrar estado
  sanitizado para operação.
- Timeout ou desconexão depois de transmitir `DATA`, sem resposta final
  conclusiva, resulta em `UNKNOWN`: repetir é permitido apenas sob a política
  explícita de melhor esforço e pode duplicar. Não marcar como entregue nem
  apagar a intenção.
- Retry de notificação nunca repete pagamento, reembolso, reserva, transição
  do pedido ou criação de etiqueta. Replay de evento mantém `eventId` original
  e respeita a deduplicação da intenção.
- Ordenação de e-mails por pedido não é garantia de SMTP. Cada mensagem inclui
  o estado e a hora do fato; antes do envio, mensagens obsoletas podem ser
  suprimidas se a leitura do estado atual provar que já não são úteis. A
  política concreta de supressão precisa ser testada em C75.

### Política inicial recomendada para C75 (proposta, não aprovada)

Usar o backoff já aprovado em C45 (1 s inicial, exponencial ×2, teto de 1 min,
full jitter) para falhas transitórias, até 8 tentativas; registrar `UNKNOWN`
para resultado SMTP ambíguo e encaminhar à inspeção/reconciliação operacional,
sem alegar entrega. A retenção deve preservar mensagens falhas/UNKNOWN enquanto
estiverem sob investigação; retenção de mensagens aceitas e dados pessoais
precisa de decisão antes da limpeza automática. C45 rege transporte de eventos,
mas não aprova automaticamente política de tentativa SMTP.

## 5. Privacidade e segurança

- Destinatário: somente o e-mail de contato imutável registrado para a compra;
  igualdade de e-mail não associa um pedido convidado a uma conta.
- Logar identificador opaco da notificação/evento, tipo, estado, tentativa,
  duração e classe sanitizada do erro. Não logar endereço de e-mail completo,
  assunto/corpo, token, código de retirada, URL com segredo nem resposta SMTP
  que possa contê-los.
- Não incluir em assunto/corpo endereço postal completo, lista de itens,
  diagnóstico interno, dados de pagamento ou detalhes desnecessários do
  pedido. Link de acesso deve apontar somente ao pedido correspondente e
  receber a proteção/validade definida por `orders`.
- E-mails são transacionais desta demonstração. Não adicionar marketing,
  rastreadores, pixels de abertura, conteúdo remoto ou preferência de
  newsletter nesta fatia.
- Mailpit é o destino local de desenvolvimento e teste (C08), não evidência de
  entrega a um provedor público. Remetente, domínio de envio, TLS, credenciais,
  SPF/DKIM/DMARC e homologação permanecem sujeitos a C04 e configuração fora
  do repositório; nunca commitar segredos.

## 6. Fronteira de falha SMTP

| Resultado SMTP | Estado local | Efeito de negócio | Afirmação permitida |
|---|---|---|---|
| Conexão indisponível antes do envio | `RETRY` ou falha permanente por configuração | Nenhum rollback; pedido/pagamento seguem | “E-mail ainda não enviado” |
| SMTP retorna aceitação final | `SENT_TO_SMTP` | Nenhum | “Servidor aceitou para envio”; não “chegou à caixa” |
| Timeout/desconexão sem saber se aceitou | `UNKNOWN` | Nenhum | “Resultado de envio desconhecido”; possível duplicata em retry |
| Endereço rejeitado permanentemente | `PERMANENT_FAILURE` | Nenhum; demais meios de consultar o pedido continuam | “Não foi possível enviar”; correção/contato seguem política de produto |

Não usar o e-mail como canal único para consultar checkout, pedido,
cancelamento, reembolso ou retirada. O produto mantém as superfícies seguras
existentes como fonte do estado.

## 7. Escopo e mapa de capacidades

| Responsabilidade | Dono | Fronteira |
|---|---|---|
| Fatos duráveis e estados de pedido/pagamento | `orders`, `payments`, `checkout` | Emitem fatos sem e-mail, endereço ou token no payload |
| Consumo e deduplicação | `eventing` + handler `notifications` | Processa `eventId`; retry SMTP não repete efeito comercial |
| Composição de mensagens e entrega | `notifications` | Seleciona matriz, resolve destinatário via identificador/port seguro, entrega por SMTP |
| Verificação/recuperação de identidade | `identity` | Mantém templates e intenção próprios; não importa `notifications` |
| Transporte de demonstração | infraestrutura C08/Mailpit | SMTP local + interface HTTP para inspeção manual, sem valor como homologação pública |

Não criar um serviço genérico de fan-out, não colocar PII no envelope de
eventos e não permitir que o handler de notificações altere pedido ou
pagamento.

## 8. Verificação planejada para C75

- Testes unitários da matriz: cada fato mapeado produz a mensagem correta;
  fatos não listados e eventos duplicados não produzem mensagem adicional.
- Integração PostgreSQL/Kafka: outbox → handler → intenção persistida,
  redelivery idempotente, ordenação por pedido e recuperação após restart.
- Integração SMTP/Mailpit: aceitação capturada, indisponibilidade temporária,
  rejeição permanente e resultado ambíguo; nenhuma falha altera pagamento ou
  pedido.
- Inspecionar manualmente no Mailpit: destinatário, assunto, conteúdo,
  links e ausência de endereço completo, token/código, segredo, PII excessiva
  e afirmação de pagamento/entrega não confirmada.
- `npm --prefix frontend run docs:check`
- `git diff --check`

## 9. Critérios de aceitação documental — C74

| ID | Critério | Evidência |
|---|---|---|
| NOT-DOC-1 | Cada mensagem proposta identifica fato, destinatário e conteúdo mínimo | revisão da matriz §3 contra `SPEC-orders`, `SPEC-payments` e `SPEC-eventing` |
| NOT-DOC-2 | Duplicação, retry, falha ambígua e limite SMTP não prometem `exactly-once` nem entrega à caixa postal | revisão §4/§6 contra garantias de C45 e semântica SMTP |
| NOT-DOC-3 | Dados pessoais, token e código de retirada ficam fora de eventos/logs e do conteúdo desnecessário | revisão §3/§5 contra ORD-Q02 e D71 |
| NOT-DOC-4 | Dependência de Mailpit e falta de homologação pública ficam explícitas | revisão §5/§8 contra C04 e C08 |
| NOT-DOC-5 | Itens ainda sem decisão estão rotulados como propostas para revisão | revisão humana desta spec antes de C75 |

## 10. Perguntas para revisão antes de C75

1. Confirmar a matriz de eventos: quais atualizações intermediárias devem
   chegar por e-mail (`PREPARING`, `DECLINED`, `UNDER_REVIEW`) e se pedido
   cancelado e reembolso devem gerar mensagens separadas.
2. Confirmar se `READY_FOR_PICKUP` deve enviar somente o aviso e link seguro
   (proposta; nunca o código de uso único definido por D71).
3. Confirmar política de tentativas SMTP: a recomendação é até 8 tentativas
   com backoff C45 e estado operacional para `UNKNOWN`; o resultado ambíguo
   pode duplicar uma mensagem se repetido.
4. Definir remetente/domínio público e retenção de mensagens/intents quando
   C04 for homologada. Sem essa decisão, C75 usa Mailpit apenas e não afirma
   entrega externa.

Silêncio não é aprovação. C75 deve implementar apenas as decisões revisadas e
manter as demais como limites explícitos.
