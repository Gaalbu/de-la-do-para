# Spec da vitrine pública — C33

## 0. Metadados

- Módulo: `storefront`
- Status: proposta implementável, dependente de revisão do contrato de consulta
- Dependências: catálogo/procedência (C17/C22), inventário (C25/C27), preço
  canônico (C30) e direção visual aprovada em C03
- Escopo: descoberta pública, renderização SSR e estados de leitura; carrinho,
  checkout e autenticação ficam nas specs próprias

## 1. Objetivo e limites

A vitrine permite que uma pessoa encontre produtos ativos, entenda sua origem,
veja uma faixa de preço e saiba se existe disponibilidade para compra. A
consulta é pública e somente de leitura. O servidor é a fonte de verdade para
produto, SKU, preço e disponibilidade; o navegador não calcula nem inventa
estado comercial.

Produtores e produtos de demonstração devem continuar identificados como
fictícios quando essa informação aparecer. A vitrine não expõe coordenadas,
endereço residencial, dados de fornecedor ou qualquer dado de sessão.

## 2. Rotas e URLs compartilháveis

| Rota | Objetivo | Renderização inicial |
|---|---|---|
| `/` | entrada editorial e descoberta | SSR sem sessão |
| `/catalog` | lista filtrável e paginada | SSR da primeira página |
| `/products/:slug` | detalhe do produto e variantes | SSR do conteúdo público |
| `/producers/:slug` | origem e produtos do produtor | SSR do conteúdo público |

Filtros da lista vivem na query string para que copiar, recarregar e usar
voltar/avançar preserve a mesma consulta. Parâmetros desconhecidos ou inválidos
não devem produzir uma consulta ampla acidental: retornam erro de validação
estável ou são removidos de forma explícita pela UI.

Parâmetros previstos para `/catalog`:

- `producer`: slug do produtor;
- `category`: `FOOD` ou `CRAFT`;
- `minPrice` e `maxPrice`: centavos BRL inteiros, inclusive nas bordas;
- `sort`: `relevance`, `price-asc`, `price-desc` ou `name-asc`;
- `page`: inteiro positivo, começando em 1;
- `size`: inteiro limitado pelo servidor, com padrão documentado no contrato.

Filtros combinam por interseção. Ordenação precisa ser estável e incluir um
desempatador determinístico (identidade/slug do produto). A API deve devolver
metadados de página suficientes para desabilitar anterior/próxima sem inferir
quantidade pelo tamanho da resposta.

## 3. O que é público

Um produto só aparece na vitrine se o produto, seu produtor e pelo menos um SKU
vendável estiverem ativos. Cada cartão mostra nome, categoria, origem ampla,
preço mínimo do SKU ativo e um estado de disponibilidade derivado do saldo
livre. Saldo reservado nunca é apresentado como disponível.

O detalhe mostra descrição editorial, origem, conservação quando aplicável,
embalagem, dimensões/peso de envio, imagem principal validada, variantes e
preço de cada SKU ativo. Campos alimentares seguem as regras do catálogo; um
campo ausente não é substituído por texto inventado.

Estados públicos mínimos:

| Estado | Comportamento |
|---|---|
| disponível | preço e ação de compra podem ser exibidos |
| temporariamente indisponível | motivo comercial curto e ação desabilitada |
| produto inexistente/desativado | resposta 404 pública, sem vazar estado administrativo |
| filtro sem resultados | resumo dos filtros e ação para limpar |
| falha/indisponibilidade da API | mensagem recuperável e tentativa explícita |
| carregando no navegador | mantém contexto e foco; não pisca conteúdo enganoso |

Uma imagem ausente usa uma composição textual acessível, nunca um placeholder
genérico que pareça uma fotografia do produto.

## 4. SSR, sessão e hidratação

- O HTML inicial de catálogo, produto e produtor contém título, resumo e dados
  públicos relevantes para leitura sem JavaScript.
- SSR não lê cookie de sessão, não chama endpoint administrativo e não inclui
  preço personalizado, carrinho ou PII.
- Cache público, se adotado, varia somente por URL e versão de dados públicos;
  nunca pode compartilhar conteúdo de sessão entre usuários.
- A hidratação preserva URL, filtros, foco lógico e estado de erro. Uma busca
  iniciada depois não pode ser sobrescrita pelo resultado de uma busca antiga.
- O título, descrição e canonical refletem a rota/consulta atual; paginação não
  deve criar links canônicos conflitantes.

## 5. Direção visual e acessibilidade

A implementação segue C03: fundo marfim, verde profundo para texto e ação,
terracota somente em títulos grandes, ícones e divisórias, Fraunces em títulos
e Inter no corpo. Fotos específicas do catálogo lideram a composição; não usar
gradientes roxo-azulados, blobs, vidro fosco ou conteúdo genérico.

Critérios verificáveis:

- fluxo de catálogo, detalhe e retorno funciona com teclado;
- filtros têm nome acessível, foco visível e anúncio de resultados/erros;
- diálogos e menus, se existirem, devolvem foco ao gatilho;
- texto principal permanece legível em viewport móvel e zoom de 200%;
- movimento respeita `prefers-reduced-motion`;
- terracota não é usada como texto pequeno sobre marfim;
- estados de indisponibilidade não dependem apenas de cor ou ícone;
- imagem tem `alt` específico; imagem decorativa tem `alt=""`;
- loading e erro não deixam foco preso nem removem silenciosamente o contexto.

## 6. Critérios de aceitação

| ID | Critério | Evidência |
|---|---|---|
| STF-001 | URL compartilhada reproduz filtros, ordenação e página | teste de rota/reload |
| STF-002 | Filtros combináveis têm limites e erro previsível | contrato + teste de consulta |
| STF-003 | Produto inativo, produtor inativo e SKU inativo não vazam para a vitrine | teste de integração |
| STF-004 | Disponibilidade usa saldo livre, nunca reservado | teste com reserva ativa |
| STF-005 | HTML SSR contém conteúdo público e nenhum dado de sessão | inspeção de HTML + teste SSR |
| STF-006 | Estados vazio, erro, carregamento e indisponível são recuperáveis | E2E |
| STF-007 | Jornada é operável por teclado, móvel e movimento reduzido | E2E/a11y + inspeção manual |
| STF-008 | Conteúdo fictício e limites de dados são identificáveis | revisão contra C03/D65 |

## 7. Verificação

Antes de marcar C33 concluída:

```bash
npm run docs:check --prefix frontend
git diff --check
```

Revisar manualmente esta spec contra `SPEC-catalog.md`, `SPEC-inventory.md`,
`SPEC-pricing.md`, `docs/design/brief.md` e `docs/design/storyboard.md`.
O contrato executável de consultas e a implementação ficam para C34; esta
spec não aprova silenciosamente combinações de cupons nem decisões de checkout.
