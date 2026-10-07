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

### Compra e geração de etiquetas (C70)

- Iniciar somente para pedido pago na modalidade `DELIVERY`, por ação
  administrativa autenticada. Cada unidade de expedição mantém operação e
  identidade local estáveis, associadas às sequências do snapshot; repetir o
  comando devolve o estado persistido sem reenviar uma chamada externa iniciada.
  A unicidade local é pedido + unidade de expedição + etapa; a versão otimista
  rejeita gravações concorrentes obsoletas.
  Cada POST de carrinho cria uma etiqueta e retorna um ID. A relação
  unidade↔pacotes/IDs do provedor deve seguir o serviço e a composição
  confirmados na cotação e em C04; não recalcular ou dividir pacotes depois do
  aceite. Correios (serviços 1, 2, 17), J&T, Loggi e serviço 27 não aceitam
  vários volumes por etiqueta; criar uma chamada separada por pacote somente
  quando isso corresponder à composição e ao preço cotados.
- O envio usa um snapshot imutável capturado no aceite da compra: composição e
  sequência dos pacotes, dimensões/peso protegidos, linhas/quantidades/valores
  declarados, serviço cotado e endereço de destino. Não reconstruir pacotes nem
  consultar catálogo/endereço editável durante a expedição.
- Cada operação `PURCHASE` ou `GENERATE` é criada com o ID conhecido da etapa
  anterior; sem esse ID, não pode sair de `READY`. O mesmo ID permanece na
  operação mesmo quando a resposta de checkout/geração não repete o valor.
- Fluxo Melhor Envio: inserir envio no carrinho
  ([`POST /api/v2/me/cart`](https://docs.melhorenvio.com.br/reference/inserir-fretes-no-carrinho)),
  comprar o ID retornado
  ([`POST /api/v2/me/shipment/checkout`](https://docs.melhorenvio.com.br/reference/compra-de-fretes-1))
  e gerar a etiqueta para o mesmo ID
  ([`POST /api/v2/me/shipment/generate`](https://docs.melhorenvio.com.br/reference/geracao-de-etiquetas)).
  Persistir cada resposta antes de iniciar a próxima etapa; chamada externa
  nunca ocorre dentro da transação PostgreSQL. A configuração aceita somente a
  base sandbox e a UI/API identifica a etiqueta como teste.
- A documentação do provedor não promete idempotência para essas operações.
  Timeout, conexão interrompida após o envio ou resposta ilegível em uma
  operação de escrita deixam a etapa `UNKNOWN`; não reenviar automaticamente,
  nem iniciar outra compra para o mesmo pacote. A consulta por ID pode atualizar
  um envio conhecido
  ([`POST /api/v2/me/shipment/tracking`](https://docs.melhorenvio.com.br/reference/rastreio-de-envios));
  uma etapa sem ID recuperável fica em análise para a operação administrativa
  futura C82b. Erro determinístico de validação pode ficar `FAILED` sem apagar
  evidência anterior.
- C71 sincroniza o ciclo de vida por ID conhecido de etiqueta. A resposta de
  tracking é um objeto indexado pelo ID consultado; aceitar somente a entrada
  cuja chave e campo `id` correspondam ao pedido. Guardar apenas status/evento,
  timestamp local de observação, IDs e sequências vinculadas; nunca guardar o
  corpo integral do provedor nem tracking code no ledger. Usar eventos assinados
  HMAC-SHA256 (`X-ME-Signature`) quando disponíveis e suportar consulta sandbox
  com uma etiqueta por requisição. Status fora de ordem não regride o maior
  estágio persistido. Exceções do transporte são registradas sem apagar estágio
  já alcançado. Só agregar estado do pedido após todos os pacotes do manifesto
  imutável estarem entregues; a relação real ID↔volume e tempos do sandbox ficam
  sujeitos à prova C04.
- Uma falha em uma unidade de expedição não reverte nem compra novamente
  etiquetas concluídas nas outras unidades. O resultado da ordem mostra o
  vínculo unidade↔pacotes, etapa, IDs do provedor quando conhecidos, correlação
  e erro sanitizado. Credenciais, corpo
  integral da requisição/resposta, documentos e endereço não entram em logs,
  métricas ou mensagens de erro.
- A chamada ao sandbox requer campos obrigatórios de remetente, destinatário e
  produtos declarados. Para envios comerciais, a referência exige chave da
  nota fiscal e inscrição estadual do remetente; o fluxo não comercial usa
  declaração de conteúdo e inscrição vazia/`ISENTO`. Desde 06/04/2026, a API
  requer `products` corretos para a integração DC-e. A política fiscal e a
  disponibilidade das respectivas chaves/documentos são pré-requisitos para
  envio real ao sandbox. C70 não cria esses dados: remetente/documentos de
  origem precisam vir de configuração autorizada, e os campos pessoais
  ausentes do pedido precisam ser resolvidos na jornada de compra antes da
  homologação.
- Pré-requisito de implementação: em `origin/main` (`ec02daa`), o aceite guarda
  itens/preço no pedido, mas não associa o pedido ao `snapshotId`/`quoteId` nem
  persiste geometria e conteúdo de cada pacote; `shipping_quotes` guarda somente
  as sequências dos pacotes. A expedição não pode reconstruir isso do catálogo
  atual. O aceite precisa gravar e expor o manifesto imutável ao módulo
  `shipping` antes de C70 poder cumprir o aceite acima.
- Contrato semântico necessário para o seam `orders` → `shipping` (proposta
  técnica; shape Java fica com o módulo `orders`): uma leitura de expedição deve
  ocorrer sob o mesmo lock que valida o estado elegível do pedido e retornar a
  projeção capturada no aceite, nunca dados recebidos do navegador. Ela deve
  identificar pedido, snapshot e versão, cotação aceita e fingerprint, serviço
  e valores/prazo aceitos, destino de entrega e cada pacote com identidade
  estável, sequência, fingerprint, medidas externas, peso protegido e alocação
  de linhas/quantidades/valores declarados. Assim o adapter consegue gerar
  `products` e `volumes` sem consultar SKU, preço, embalagem ou endereço
  editável atual. O contrato não presume que um pacote equivale a uma etiqueta
  ou unidade de expedição: qualquer agrupamento precisa ser o que foi aceito na
  cotação e comprovado no C04; sem prova, a operação deve falhar antes de fazer
  escrita externa. Se faltar qualquer campo obrigatório ao provedor, falhar
  fechado antes do `POST /cart`, sem preencher com dado de demonstração.
- O seam expõe destino e conteúdo somente ao caso administrativo autorizado que
  inicia a expedição; não os inclui em respostas de consulta geral, logs,
  métricas, eventos ou mensagens de erro. A origem e documentos fiscais vêm de
  configuração local opt-in autorizada, fora do manifesto do comprador. O
  `shipping` persiste a identidade/fingerprint da manifestação usada em cada
  operação e rejeita retomada quando o manifesto não coincide com o persistido.
  Nenhum módulo lê diretamente tabelas privadas de outro módulo.
- O spike C04 ainda precisa demonstrar quais serviços aceitam a composição,
  se a resposta por unidade já identifica o ID de forma parseável, como a
  composição cotada mapeia às chamadas individuais de carrinho/IDs e como
  reconciliar uma resposta perdida. Checkout e geração aceitam arrays de IDs,
  mas não documentam atomicidade nem resultado parcial por ID; enviar cada ID
  separadamente até a homologação demonstrar semântica segura de lote. O
  contrato local deve preservar o resultado por unidade sem presumir
  atomicidade do provedor.
- O cliente HTTP da C70 é opt-in (`SHIPPING_MELHOR_ENVIO_ENABLED`) e envia
  somente para `https://sandbox.melhorenvio.com.br`; exige token e `User-Agent`
  com contato técnico. Limita conexão/leitura a 3/10 segundos. Só a resposta
  HTTP 422 documentada é rejeição definitiva; demais falhas HTTP, transporte,
  resposta ilegível ou sucesso sem ID parseável mantêm `UNKNOWN`. O adapter não
  interpreta sucesso agregado de compra/geração como sucesso de cada ID e não
  persiste nem registra corpos de resposta.
- Até C04 confirmar segurança de lote, cada requisição de compra/geração contém
  exatamente um ID conhecido em `orders`; a API aceita arrays, mas isso não
  prova atomicidade nem resposta individual.

### Confirmação de retirada (C72, D71)

- Ao passar um pedido `PICKUP` para `READY_FOR_PICKUP`, gerar um código aleatório
  de uso único e manter o material necessário para validação e reapresentação
  ao cliente sem persistir o código em texto claro.
- Mostrar o código apenas em `GET /api/v1/orders/{id}/pickup`, depois de
  autorizar a sessão dona ou `X-Order-Token` daquele pedido. A resposta é
  `private, no-store`; código não aparece na listagem/admin, em outbox, logs,
  métricas ou URL.
- A tela também mostra o ponto congelado no pedido, a janela de retirada e a
  regra de guarda de três dias úteis após ficar pronto. Não calcular uma data
  final até existir calendário operacional aprovado e configurado (C25).
- Somente admin pode iniciar preparação, marcar como pronto e confirmar
  retirada. A confirmação exige o código atual; pedido/modo/estado são
  validados sob o lock do pedido. Confirmação e cancelamento competem pelo
  mesmo lock, então só uma transição pode vencer. Depois de `PICKED_UP`, o
  código deixa de ser exibido e não pode ser reapresentado.
- Cifrar o código em repouso com chave de 32 bytes configurada fora do Git;
  consumi-lo atomicamente com a transição. Persistir estado de preparação/
  retirada no módulo `shipping`; usar portas de aplicação de `orders` para
  transições/histórico. Não importar repositories de `orders`.

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
