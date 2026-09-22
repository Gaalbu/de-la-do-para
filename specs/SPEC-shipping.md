# SPEC — Cotação, expedição e retirada

Status: contrato aprovado para planejamento de shipping (C41); não implementa
transportadora, pagamento, reserva ou checkout.

## Objetivo e limites

Shipping transforma um snapshot imutável de compra em alternativas de entrega
ou retirada. Não é fonte de verdade de preço, estoque ou identidade: recebe
quantidades do snapshot, consulta dimensões/massa do SKU e devolve uma opção
com validade explícita. Nenhuma chamada externa é feita nesta etapa.

## Identidade e validade da cotação

Uma cotação é calculada para `snapshotId`, `snapshotVersion`, linhas do
snapshot, destino postal normalizado quando aplicável, calendário operacional,
versão das políticas e composição determinística dos pacotes.

O servidor calcula um `inputFingerprint` canônico com esses campos e rejeita a
aceitação quando ele não corresponde ao snapshot, destino, política ou pacotes
atuais. O fingerprint não contém segredo e não substitui autorização.

Cada cotação tem `quoteId`, `createdAt`, `expiresAt`, `currency`, custo em
centavos, prazo de transporte e prazo de preparação separados. Depois de
`expiresAt`, não pode ser aceita.

## Modalidades

### Entrega

O destino é validado antes da cotação. A previsão soma preparação, suspensões
do calendário operacional e transporte. O CEP `66053-000` é referência
sintética e não comprova atendimento comercial. Dados de adapter externo são
não confiáveis até validação.

### Retirada

Na v1, usa o ponto fictício `Ponto de demonstração — Belém`, de segunda a
sexta, 9h–18h no horário de Belém, somente quando o pedido estiver pronto. O
prazo de preparo é separado da janela de retirada. Chocolate 70% fica restrito
à retirada conforme D55; itens incompatíveis não são removidos silenciosamente.

## Pacotes

O snapshot pode gerar vários pacotes, cada um com identidade estável,
`packageFingerprint`, linhas alocadas uma única vez, massa e dimensões brutas.

1. Separar alimentos e artesanato quando a política exigir.
2. Colocar cada peça frágil em pacote próprio.
3. Usar dimensões e massa brutas do SKU; nunca reduzi-las para cotar menos.
4. Para frágil, acrescentar 6 cm por dimensão e 100 g por peça; para não
   frágil, 1 cm por face interna e 50 g por pacote.
5. Rejeitar item incompatível com modalidade, caixa ou massa sem alterar o
   carrinho. A resposta oferece retirada integral ou remoção explícita com
   recálculo.

Uma linha não pode aparecer em dois pacotes, e toda unidade deve aparecer uma
vez. A mesma entrada, política e versão devem produzir o mesmo ordenamento e
fingerprints.

## Expedição e cancelamento

O estado de cada pacote é `PREPARING → READY → LABEL_CREATED →
HANDED_TO_CARRIER → IN_TRANSIT → DELIVERED`, ou `READY → PICKED_UP` para
retirada. Emitir etiqueta não equivale a entregar à transportadora.

Após um pacote ser entregue à transportadora, os demais continuam por padrão.
Pausar os restantes exige ação administrativa explícita; não há cancelamento
ou reembolso parcial automático.

- Entrega: cancelamento direto até `HANDED_TO_CARRIER`; etiqueta emitida ainda
  permite cancelamento. Depois, abrir análise sem prometer reembolso.
- Retirada: cancelamento até a confirmação concorrente de `PICKED_UP`, inclusive
  em `READY`; se pago, iniciar reembolso integral em sandbox.
- Cancelamento e confirmação são operações concorrentes, resolvidas por
  versão/lock do pedido.
- Pedido pronto fica guardado por três dias úteis; depois abre análise, sem
  cancelar, descartar ou reembolsar automaticamente, mantendo o estoque.

## Erros e invariantes

`400` para destino/modalidade/payload inválido; `404` para snapshot/opção
indisponível; `409` para fingerprint, versão ou estado incompatível; `410`
para cotação expirada; `422` para item sem modalidade compatível. Problemas
usam `codigo`, `correlationId` e não expõem credenciais de adapters.

Shipping não altera o carrinho. Aceitar uma cotação exige revalidar snapshot,
disponibilidade, preço, fingerprint e validade dentro da transação de checkout.

## Pendências deliberadas

- C41a implementa o algoritmo puro e determinístico de composição; persistência
  da composição fica para o snapshot de checkout (C43).
- C42 define adapters, timeout, credenciais e parser do sandbox.
- C43 define persistência e aceitação vinculada ao snapshot.
- O calendário de feriados/pontos facultativos será conferido em fontes
  oficiais antes de virar fixture operacional.

## Verificação

```bash
npm run docs:check --prefix frontend
git diff --check
```

Revisar contra D17, D18, D21–D31, D35–D37 e D55–D60 em
`specs/SPEC-logistics.md` antes de implementar cada capability.
