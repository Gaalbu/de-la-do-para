# Spec de políticas logísticas — C24a

## 0. Metadados

- Módulo: `shipping` (políticas aprovadas antecipadas; contratos detalhados em C41)
- Status: proposta consolidada para revisão do usuário; sem implementação
- Decisões base: D17–D18, D19–D20, D21–D31, D35–D37, D54–D60, D66–D68
- Dependências: catálogo (`catalog`), inventário (`inventory`), pedidos (`orders`), preço (`pricing`); integração externa permanece em `integrations`

## 1. Objetivo

Registrar políticas de origem de demonstração, preparação, retirada, validade
comercial, cancelamento, embalagem e cupons já aprovadas. As regras dão base
às specs de inventário, preço, pedidos e expedição; não comprovam capacidade
de provedor nem operação física real.

## 2. Políticas confirmadas

1. **Origem:** CEP de teste `66053-000`, Belém/PA, é referência postal e não
   implica vínculo comercial com o local de referência (D21).
2. **Retirada:** ponto fictício “Ponto de demonstração — Belém”, sem
   atendimento presencial nem retirada real; seg–sex, 9h–18h, horário de
   Belém, somente após o pedido ficar pronto (D22–D23).
3. **Preparação:** concluir até 18h do próximo dia útil após a confirmação do
   pagamento, no horário de Belém. O prazo de preparação é separado do prazo
   da transportadora e somado à previsão de transporte (D24–D25).
4. **Calendário:** feriados nacionais, estaduais do Pará e municipais de
   Belém suspendem preparação e retirada. Calendário local configurável,
   separado do calendário informado pela transportadora e sem API paga;
   fontes oficiais serão verificadas na implementação. Ponto facultativo só
   suspende quando cadastrado explicitamente (D26).
5. **Alimentos:** cada SKU alimentar define dias mínimos de validade restantes
   na chegada prevista. Os valores de D54 são margens fictícias de regra de
   software, não aconselhamento de conservação. Estoque seleciona lotes; não
   altera sua validade original (D27–D28, D54).
6. **Cancelamento de entrega:** permitido diretamente até a entrega física do
   pacote à transportadora; etiqueta emitida não significa despacho (D29).
   Se qualquer pacote do pedido já foi entregue à transportadora, bloquear o
   cancelamento direto do pedido inteiro e permitir solicitação de análise,
   sem reembolso automático. Cancelamento/reembolso parcial não estão
   autorizados (D30).
7. **Retirada cancelada:** aceitar cancelamento até o atendente confirmar a
   retirada, inclusive enquanto estiver pronta. Se paga, iniciar reembolso
   integral em sandbox. Cancelamento e confirmação da retirada são
   mutuamente exclusivos sob concorrência (D31).
8. **Guarda de retirada:** manter o pedido guardado por três dias úteis após
   ficar pronto. Ao fim do prazo, abrir análise administrativa; não cancelar,
   descartar ou reembolsar automaticamente, e manter o estoque comprometido
   até resolução explícita (D66).
9. **Pacotes:** compra pode ser dividida em vários pacotes; separar alimentos
   e artesanato, e colocar cada peça frágil em pacote próprio (D18, D35).
   Chocolate 70% é somente retirada na v1 (D55). Se item não puder ser
   enviado, oferecer retirada do pedido inteiro ou remoção explícita do item
   com recálculo; não combinar modalidades nem alterar carrinho sem escolha
   explícita (D37).
10. **Dimensões e proteção:** medidas e massas brutas vêm do SKU (D57), sem
   redução para obter cotação. Caixas P/M/G, capacidades e massa constam em
   `docs/decisions.md` (D59). Proteção simulada: frágeis com acréscimo de 6 cm
   por dimensão e 100 g por peça (D58); não frágeis com 1 cm por face interna
   e 50 g por pacote, sem somar esse papel aos frágeis (D60). Isso não comprova
   proteção física.
11. **Cupons relacionados a reembolso:** uso ligado ao e-mail verificado só é
    devolvido após reembolso integral confirmado; manter histórico e respeitar
    validade e limite global. Pedido de reembolso ou estado pendente não
    libera reutilização (D33–D34).

## 3. Limites e perguntas reservadas

- O dataset de lotes segue a fixture sintética aprovada em D67; isso não
  define estoque comercial inicial. C25/C41 ainda precisam fechar um
  calendário reproduzível e conferido em fontes oficiais antes de usá-lo.
- Validade do alimento em retirada tardia fica para as specs de
  `orders`/`shipping`; não decidir descarte ou reembolso automático por
  inferência (o prazo de guarda segue D66).
- Critérios administrativos para concluir uma solicitação após despacho
  parcial ficam para checkout/pagamentos/expedição. A solicitação não é
  cancelamento nem autorização de reembolso.
- Reserva/liberação do limite global de cupons fica para `SPEC-pricing.md`;
  D33–D34 não significam reset ilimitado do contador global.
- Cotação, aceitação de embalagem e prazos precisam de homologação no sandbox
  (C04/C42). Dados D57–D60 são referências simuladas.

### Recomendações para revisão nas etapas correspondentes

As recomendações abaixo ainda não são regras aprovadas. Perguntar uma de cada
vez antes da implementação que dependa dela.

| Lacuna | Momento | Recomendação a apresentar | Motivo |
|---|---|---|---|
| Datas do calendário operacional | C25/C41 | Arquivo local versionado por ano, composto por feriados nacionais, estaduais do Pará e municipais de Belém confirmados em fontes oficiais. Ponto facultativo entra somente por cadastro explícito, como D26 já determina. | Reproduzível sem API paga e separado do calendário da transportadora |
| Quantidades e datas de lotes demonstrativos | C25 | **Aprovado em D67:** fixture marcada como sintética, com relógio fixo; por alimento, um lote exatamente na margem D54 e outro um dia abaixo. Não representa estoque inicial real. | Testa a fronteira comercial sem inventar procedência ou disponibilidade real |
| Guarda e não comparecimento na retirada | Respondida em C24a (D66) | **Aprovado:** três dias úteis após ficar pronto; depois, análise administrativa, sem ação automática e com estoque comprometido até resolução. | Dá prazo claro ao cliente e evita efeito financeiro/estoque irreversível por ausência presumida |
| Pedido com despacho parcial | Specs de checkout/pagamentos/expedição | Após um pacote ser entregue à transportadora, os demais continuam o fluxo normal por padrão. Pausar os ainda não despachados exige decisão administrativa; não há cancelamento, reembolso automático ou reembolso parcial. | Preserva D30 e explicita a continuidade operacional aprovada em D68 |

### Referências oficiais consultadas para calendário

Para 2026, a página de atos do [MGI lista a Portaria nº 11.460/2025](https://www.gov.br/gestao/pt-br/acesso-a-informacao/institucional/atos-normativos/2025/2025-portarias), cujo calendário se aplica à administração pública federal; o [Decreto estadual do Pará nº 5.122/2025](https://www.semas.pa.gov.br/legislacao/files/pdf/846515.pdf) lista feriados e pontos facultativos do Executivo estadual. Belém publicou atos municipais por período, como os Decretos 114.321/2026 (abril/maio) e 114.409/2026 (Corpus Christi), divulgados pela [Agência Belém](https://agencia.belem.pa.gov.br/notas/veja-os-pontos-facultativos-e-feriados-de-abril-e-maio-em-belem/) e [Agência Belém](https://agencia.belem.pa.gov.br/notas/feriado-e-ponto-facultativo-alteram-servicos-municipais-em-belem/). Essas fontes ajudam a localizar datas e atos, mas calendários de expediente público não serão copiados como feriados da loja: incluir feriado quando confirmado como tal e ponto facultativo somente quando explicitamente cadastrado, conforme D26. A lista completa de Belém do ano de operação será verificada em sua publicação oficial antes da fixture de calendário.

## 4. Critérios de aceitação documental

| ID | Critério | Evidência |
|---|---|---|
| LOG-001 | D21–D31 transcritas sem mudar marco de despacho ou cancelamento | revisão contra `docs/decisions.md` |
| LOG-002 | D35–D37 e D55–D60 aplicadas sem alegar capacidade física real | exemplos de pacotes na spec de shipping futura |
| LOG-003 | Pendências atribuídas à spec/tarefa correta e sem decisão silenciosa | tabela de pendências §3 e revisão humana |

## 5. Verificação

```bash
npm run docs:check --prefix frontend
git diff --check
```

Revisar manualmente a correspondência com `docs/decisions.md` e `docs/PLANO-MESTRE.md`.
