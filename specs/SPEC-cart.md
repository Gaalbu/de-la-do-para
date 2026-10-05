# SPEC — Carrinho convidado e snapshot de compra

Status: contrato funcional C38; política C80 aprovada em 2026-09-25 (D76).
Operações de checkout e pagamento seguem suas próprias specs.

## Objetivo e limites

O carrinho representa a intenção atual de compra de um visitante ou cliente.
A compra é convidada por padrão; conta não é requisito para adicionar itens,
consultar o carrinho ou iniciar checkout. O pedido futuro recebe um snapshot
próprio: alterações posteriores no carrinho nunca removem itens do checkout já
iniciado.

O carrinho não é dono de preço, estoque, frete ou desconto. Ele guarda
referências e quantidades; consultas de catálogo/preço/disponibilidade e
cotação de entrega são revalidadas antes do checkout.

## Proprietário e acesso

- Visitante: identificado por uma sessão de compra protegida, com identificador
  aleatório e não previsível; o identificador bruto não é persistido como
  credencial reutilizável.
- Cliente autenticado: o carrinho é associado à conta após login, sem expor
  carrinhos de outra conta.
- Administrador não acessa carrinhos por estas operações públicas; ferramentas
  administrativas terão contrato separado.
- O cookie de sessão segue a política de identidade aprovada em D64
  (HttpOnly/Secure/SameSite e CSRF quando a operação alterar estado).

## Modelo mínimo

`Cart` contém `id`, proprietário (`guestSessionId` ou `accountId`), `version`,
estado aberto/checkout-iniciado e timestamps. Cada `CartItem` contém `skuId`,
quantidade inteira positiva e timestamps. A combinação
`(cartId, skuId)` é única.

O carrinho não copia nome, preço, imagem ou disponibilidade como fonte de
verdade. A resposta pode carregar uma projeção atual para exibição, sempre
marcada como sujeita a revalidação.

## Concorrência e mutações

Toda mutação envia a versão que o cliente leu (`expectedVersion`). O servidor
faz atualização otimista dentro de transação:

1. autentica o proprietário e carrega o carrinho;
2. verifica estado aberto e `expectedVersion`;
3. valida SKU e quantidade;
4. grava itens e incrementa `version` uma vez;
5. retorna o carrinho e a nova versão.

Versão divergente retorna `409` com código estável e o estado atual; não faz
merge silencioso nem descarta a alteração concorrente. Repetição segura deve
usar uma chave de operação futura definida pelo contrato de mutações, não um
`sleep` ou retry implícito.

Quantidades zero só aparecem na operação explícita de remoção. Valores
negativos, overflow, SKU duplicado no payload ou carrinho de outro proprietário
retornam erro sem alterar o agregado.

## Login e combinação

**C80 — aprovado em 2026-09-25 (D76).**

Ao autenticar, se houver carrinho convidado e carrinho da conta, o cliente
precisa escolher explicitamente uma das seguintes opções; o servidor nunca
combina silenciosamente. A API de login conclui a autenticação e informa que a
decisão está pendente. O servidor guarda a referência hash da sessão convidada
no contexto autenticado do servidor e rotaciona o identificador de sessão no
login; o identificador antigo deixa de autenticar:

- manter o carrinho da conta;
- substituir o carrinho da conta pelo convidado;
- combinar itens, somando quantidades por SKU.

A combinação é transacional: soma quantidades iguais por SKU, preserva SKUs que
ficaram inativos desde a adição ao carrinho e limita cada linha a 2.147.483.647,
o maior inteiro aceito pelo modelo persistido. A disponibilidade será
revalidada no checkout; combinação não reserva estoque. A consulta de merge
identifica SKUs inativos e a tela os marca como indisponíveis no catálogo,
explicando que serão revalidados no checkout. Se a soma exceder esse
limite, houver conflito de versão ou o carrinho de destino estiver em checkout,
nenhuma das cestas é parcialmente alterada. Substituir copia todas as linhas do
convidado, incluindo SKUs hoje inativos, sem criar duplicatas. Manter descarta
somente o carrinho convidado. Em todos os casos, as cestas são vinculadas à
conta e à sessão convidada apenas depois de a escolha persistir. A pendência
fica associada à sessão autenticada no servidor, nunca ao cliente como prova
de autorização, e é removida somente após a transação persistir.

Se só houver carrinho convidado, a opção de manter esse carrinho o associa à
conta e mantém as mesmas linhas. Se não houver carrinho convidado, a API não
solicita escolha.

## Snapshot de checkout

Iniciar checkout não consome nem limpa o carrinho. Cria um snapshot imutável
com SKU, quantidade, preço/total calculados pelo módulo dono, descontos
aplicados, modalidade de entrega/retirada quando escolhida e versão do carrinho
de origem. Itens adicionados depois do início permanecem no carrinho e não
entram no snapshot existente.

Antes de reservar, o checkout revalida preço, disponibilidade, regras de
validade e identidade do carrinho. Falha de revalidação retorna conflito
explicável e preserva o carrinho para nova tentativa. Reserva de 15 minutos,
pagamento tardio e compensação seguem as specs de inventário, pricing e
checkout; esta spec não redefine essas regras.

## Estados e erros

- `200`: consulta ou mutação aplicada, com `version` atual.
- `400`: quantidade, SKU ou payload inválido.
- `401`: sessão/conta ausente quando exigida.
- `403`: proprietário inválido ou CSRF ausente.
- `404`: carrinho ou SKU não encontrado sem revelar existência de outro dono.
- `409`: versão concorrente, carrinho em checkout ou escolha de combinação
  necessária.

Respostas usam Problem Details com `codigo` e `correlationId`; não incluem
segredos, tokens de sessão ou dados de outro proprietário.

## Critérios C38

- dois navegadores não conseguem mutar o mesmo carrinho convidado;
- reload preserva o carrinho enquanto a sessão é válida;
- duas abas com versões diferentes recebem `409` sem perda silenciosa;
- login apresenta escolha explícita para carrinhos distintos;
- snapshot não muda quando itens são adicionados depois;
- exemplos e contrato das mutações serão adicionados em C39/C39a antes do
  código de produção.
