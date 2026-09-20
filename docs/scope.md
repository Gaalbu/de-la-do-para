# Escopo aprovado e mapa de capacidades — De Lá do Pará

Transcrição do escopo confirmado pelo usuário (decisões D01–D64 em
[`decisions.md`](decisions.md)). Fonte canônica: `docs/PLANO-MESTRE.md`
(consolidação 20/09/2026). Nada aqui é inferência nova: qualquer mudança de
escopo exige decisão explícita do usuário e atualização deste arquivo antes do
código. Revisão do mapa pelo usuário é pré-requisito das specs de módulos.

## Loja

- Loja única de produtos paraenses, com procedência, produtores, entrega e
  retirada (D01). Alimentos sem refrigeração e artesanato (D02).
- Projeto de portfólio, inicialmente local, sem custo de serviços (D03).
  Implementação do zero, identidade própria; LAPES Commerce apenas como
  referência funcional — sem copiar código, identidade, dados ou histórico
  (D04).
- Spring Boot e Angular (D05); foco técnico em checkout confiável com Kafka
  (D06). Compra como convidado e conta opcional (D07).
- Integrações de teste: Asaas Sandbox e Melhor Envio Sandbox (D08), com túnel
  HTTPS temporário para webhooks (D09). Pix e cartão por página hospedada do
  Asaas (D10).
- Moeda BRL, um único ponto de expedição/retirada, venda por unidade/SKU
  (D17). Compra dividida em vários pacotes quando não couber em um (D18).
- Repositório público desde o início da implementação (D42).
- Sem prazo fixo; avanço por entregas verificadas (D14).

## Regras comerciais aprovadas (resumo; detalhe em `decisions.md`)

- Reserva de estoque por 15 minutos (D11). Pagamento confirmado após expirar a
  reserva entra em análise e inicia compensação por reembolso (D13).
- Cancelamento direto de entrega permitido antes da entrega física à
  transportadora (postagem ou coleta), mesmo com etiqueta emitida; etiqueta
  não caracteriza despacho (D29). Com qualquer pacote já entregue, bloqueio
  do cancelamento direto e solicitação para análise administrativa, sem
  reembolso automático; sem cancelamento/reembolso parcial (D30).
- Cancelamento de retirada permitido até a confirmação pelo atendente, mesmo
  com pedido pronto; reembolso integral em sandbox se pago; cancelamento e
  confirmação mutuamente exclusivos sob concorrência (D31).
- Cupons limitados por e-mail verificado, limite configurável por cupom, sem
  exigir conta; verificação por código/link, mensagens no Mailpit (D33).
  Reutilização liberada somente após reembolso integral confirmado, com
  histórico e respeitando validade e limite global (D34).
- Alimentos separados de artesanato em pacotes distintos; cada peça frágil
  (cuia, tigela, vaso) em pacote próprio (D35/D56/D58). Chocolate exclusivo
  para retirada na v1 (D55). Item incompatível com entrega: oferecer retirada
  do pedido inteiro ou remoção explícita do item, sem combinar modalidades
  no mesmo pedido (D37).
- Origem de teste: CEP 66053-000, Belém/PA, referência postal de demonstração
  sem vínculo comercial (D21). Retirada fictícia em “Ponto de demonstração —
  Belém”, sem atendimento presencial nem retirada real (D22), seg–sex 9h–18h
  (horário de Belém), somente com pedido pronto (D23).
- Preparação em até 1 dia útil após confirmação do pagamento, concluída até
  as 18h do próximo dia útil (D24/D25); feriados nacionais, do Pará e de
  Belém suspendem preparação e retirada, em calendário local configurável
  separado do transporte (D26). Validade mínima restante na chegada prevista
  configurada por produto, usada na seleção de lotes (D27; valores em D54).
- Catálogo inicial: 8 produtos fictícios — farinha de mandioca, castanha-do-
  pará, chocolate 70%, cacau em pó, cuia decorativa, cesto de fibra, tigela
  de cerâmica decorativa, vaso de cerâmica (D52/D53). Preços, unidades e
  margens em D54; medidas/pesos em D57; caixas P/M/G em D59; proteção em
  D58/D60. Dados fictícios, sem orientação de conservação real.
- Nome **De Lá do Pará** (D45); paleta fundo marfim / verde profundo /
  terracota (D46); títulos com serifa, textos/botões sem serifa (D47); tom
  acolhedor e direto (D48). Vídeo principal vertical 60–90 s + demonstração
  técnica horizontal; sequência vitrine/origem → compra → falha breve →
  recuperação sem repetir a compra (D49/D50). Fotos gratuitas com licença
  verificada e autoria registrada; sem apresentar pessoas reais como
  produtores fictícios (D51).
- Autenticação: e-mail/senha, Spring Security com sessões no PostgreSQL,
  cookie protegido e CSRF; compra convidada preservada; confirmação e
  recuperação no Mailpit (D64). Documentação da API e testes obrigatórios por
  funcionalidade (D63).

## Módulos e dependências

Monorepo com `backend/`, `frontend/`, `contracts/`, `infra/`, `docs/`,
`specs/`, `tasks/` (D62). Monólito modular Spring Boot; API e worker em
processos separados do mesmo backend; PostgreSQL e Kafka; Angular separado
(D61). Dependência = importar somente o contrato público do provedor; nenhum
acesso direto ao repository de outro módulo.

| ID | Responsabilidade e dados próprios | Depende de |
|---|---|---|
| `eventing` | Outbox, publicação Kafka, registro de consumo, tentativas e quarentena; nenhuma regra comercial | — |
| `identity` | Contas opcionais, sessões, recuperação de acesso, papéis e prova de posse do e-mail | — |
| `catalog` | Produtores, procedência, produtos, SKUs, categorias, mídia e especificações de embalagem | — |
| `inventory` | Lotes, validade, saldo, reservas e movimentações | `catalog` |
| `pricing` | Preços, cálculo monetário, cupons e reservas de uso | `catalog` |
| `cart` | Carrinho anônimo/autenticado, itens, versão e combinação no login | `identity`, `catalog`, `inventory` |
| `orders` | Snapshot comercial, estados, histórico e consultas autorizadas | `identity`, `eventing` |
| `payments` | Intenções de pagamento, adapter Asaas, webhooks, conciliação e reembolsos; recebe referência comercial opaca | `eventing` |
| `shipping` | Composição dos pacotes, cotações, modalidade, expedição, etiquetas sandbox, rastreio por pacote e retirada | `orders`, `eventing` |
| `checkout` | Orquestra compra, confirmação, expiração e cancelamento; coordena APIs públicas dos módulos | `identity`, `catalog`, `inventory`, `pricing`, `cart`, `orders`, `payments`, `shipping`, `eventing` |
| `notifications` | Entregas de notificações, templates e e-mail local no Mailpit | `orders`, `shipping`, `identity`, `eventing` |
| `storefront` | Consultas que combinam catálogo, disponibilidade e preço; sem dados comerciais próprios | `catalog`, `inventory`, `pricing` |

Fronteiras explícitas: `payments` não importa `orders`/`checkout`; `checkout`
coordena `orders`/`inventory`/`pricing` a partir dos fatos de `payments`;
`shipping` recebe o snapshot via contrato de `orders`; `eventing` nunca
importa módulos de negócio; e-mails de acesso ficam em `identity`,
comunicações comerciais em `notifications`. Transação PostgreSQL curta por
operação; nenhuma transação aberta durante HTTP externo ou espera de Kafka.
Fronteiras verificadas por Spring Modulith/ArchUnit (proposta A03).

## Ordem de construção

1. Aprovar mapa, regras pendentes, contratos e arquitetura.
2. Ambiente, aplicação mínima, qualidade e estrutura dos módulos.
3. `identity` administrativo → `catalog` → `inventory` e `pricing` → `storefront`.
4. `cart` de visitante → compra preliminar com cotação de `shipping`.
5. `eventing` → `orders` e `payments` → `checkout` com provedores controlados.
6. Adapters sandbox → conta opcional e histórico → expedição/retirada.
7. Compensações, recuperação operacional, notificações e Angular completo.
8. Homologação, provas de falhas, desempenho e release local reproduzível.

## Metas (futuras, a verificar — não resultados)

- D38: ≥80% de cobertura de branches em checkout, pagamento, estoque e preço,
  além de todos os cenários críticos (concorrência, duplicações, recuperação).
- D39: 50 compradores simultâneos por 5 min, sem estoque negativo nem efeitos
  duplicados, p95 do aceite do checkout <500 ms (aceite ≠ pagamento).
- D40: 100 eventos acumulados processados em até 60 s após dependências
  disponíveis, sem perda nem duplicação de efeito.
- D41: vitrine mobile com LCP ≤2,5 s e CLS ≤0,1 em laboratório documentado.
