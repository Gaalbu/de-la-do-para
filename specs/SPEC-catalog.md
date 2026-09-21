# Spec de catálogo e procedência — C17

## 0. Metadados

- Módulo: `catalog`
- Status: aprovada pelo usuário em 2026-09-21; C17 documental, sem implementação
- Decisões base: D01–D05, D17–D18, D27–D28, D35–D37, D45–D48,
  D51–D60, D62–D63
- Propostas: A01, A02, A05, A06, A08; usar ADRs já aceitos e decisões
  específicas do bootstrap, sem reabrir a arquitetura aprovada
- Personas: visitante da loja, administrador, executor/editor do catálogo

## 1. Objetivo ◆

Definir o conteúdo de catálogo necessário para apresentar produtos paraenses
fictícios com procedência rastreável e dados comerciais e logísticos íntegros.
O catálogo separa produtor, produto e SKU, descreve alimentos sem
refrigeração e artesanato, e fornece dimensões/pesos para que cotação e
composição de pacotes não inventem medidas.

Esta capacidade não cadastra lotes nem saldos (inventory), calcula preço,
elegibilidade por validade ou cupom (pricing), calcula disponibilidade e
preço para a vitrine (storefront), nem faz cotação ou despacho (shipping).
Não representa produtores reais, origem comprovada, aconselhamento de
conservação, certificação alimentar ou proteção de transporte validada.

## 2. Comandos ◆

```bash
git diff --check
./mvnw -Dit.test=ProducerPersistenceIT,ProducerRepositoryIT,ProducerAdminApiIT verify
npm run contracts:check --prefix frontend
npm run docs:check --prefix frontend
```

O comando Maven executa os testes de persistência e HTTP com PostgreSQL real
via Testcontainers. Os gates de contrato e documentação verificam OpenAPI,
geração TypeScript e links. A jornada browser administrativa pertence a C20.

## 3. Estrutura ◆

- `backend`: domínio e persistência próprios de produtores, produtos, SKUs,
  categorias, imagens e especificações de embalagem; migrations versionadas.
- `contracts`: schemas/versionamento dos DTOs públicos e exemplos, sem expor
  entidades JPA.
- `frontend`: administração protegida e consultas públicas acessíveis; usar
  feature de catálogo e contratos tipados gerados.
- `specs/SPEC-catalog.md`: regras e critérios desta capacidade.
- Dados comerciais e embalagens de demonstração permanecem referenciados em
  `docs/decisions.md` (D52–D60); fixture executável será criada em C26.
  Não inventar produtores concretos sem fixture aprovada; API C19 usa dados
  sintéticos identificados como demonstração em seus testes.
- Não criar lotes/saldo no catálogo, repositório genérico compartilhado,
  abstrações de mídia sem necessidade demonstrada, nem cópias do catálogo em
  `storefront`.

## 4. Estilo e convenções ◆

- Código em inglês; conteúdo público e administrativo em pt-BR.
- Identificadores técnicos estáveis e distintos de nomes exibidos. Dinheiro
  em BRL como inteiro de centavos/contrato monetário definido no módulo
  pricing; nunca usar ponto flutuante para valor comercial.
- Medidas internas do produto em milímetros inteiros e massa em gramas
  inteiros na API e persistência; apresentação pode converter para cm/kg.
  Conversão não pode arredondar para baixo ao validar encaixe ou cotação.
- Descrições de procedência identificam explicitamente que produtor, local,
  safra e narrativa são fictícios no conjunto de demonstração.
- Imagens só podem entrar no produto se a página de origem comprovar licença
  compatível com redistribuição, autoria e atribuição estiverem registradas e
  o enquadramento for aprovado. Alternativa: ilustração própria ou cartão
  tipográfico; não simular foto de produtor real.

## 5. Estratégia de testes ◆

| Critério | Teste previsto | Gatilho e asserção | Execução |
|---|---|---|---|
| CAT-001 | `ProductCategoryInvariantTest` | persistir categoria fora de `FOOD`/`CRAFT` é recusado | unitário |
| CAT-002 | `ProductRepositoryIT` | duplicar SKU conflita; produto pode ter variantes SKU e atualização preserva suas identidades/relações | PostgreSQL/Testcontainers |
| CAT-003 | `ProductCategoryInvariantTest` | SKU de alimento exige unidade/conteúdo e dias mínimos positivos; SKU de artesanato não recebe validade alimentar | unitário |
| CAT-004 | `ProductSkuPackagingTest`, `ProductRepositoryIT` | dimensões/peso ausentes, zero ou negativos são recusados; encaixe não reduz medida por arredondamento | unitário + PostgreSQL/Testcontainers |
| CAT-005 | `ProductImageLicenseTest` | imagem sem fonte, licença ou autoria verificável não pode ser publicada | unitário/validação de fixture |
| CAT-006 | `ProducerAdminApiIT` | sessão/papel/CSRF; admin cria, lista, consulta, edita e desativa produtor; respostas Problem Details correlacionadas | integração HTTP + PostgreSQL |
| CAT-007 | `CatalogPublicContractTest` | produto público não inclui campos administrativos/segredos; resposta corresponde ao schema e exemplos | contrato/HTTP |
| CAT-008 | `ProductRepositoryIT` | desativação preserva produto, SKUs e relações; FK impede exclusão física com SKUs referenciados. Snapshots independentes são responsabilidade da capacidade de pedidos | PostgreSQL/Testcontainers |
| CAT-009 | `CatalogDemoFixtureTest` | os oito SKUs e valores conferem com D52–D60 e todos são marcados como fictícios | teste de fixture |

Datas usam `Clock` controlado quando aplicável. Não aceitar sucesso e erro como
resultados equivalentes. Mudanças em schemas precisam atualizar exemplos e
geração de cliente no mesmo PR.

## 6. Limites de atuação ◆

- C24a resolve regras comerciais/logísticas ainda pendentes antes de
  inventory, pricing e shipping; esta spec registra apenas seus limites.
- C03 revisa identidade visual e assets. `docs/design/assets.md` ainda lista
  sete imagens por curar; até licença, autoria e enquadramento serem
  verificados, usar fallback tipográfico/ilustração própria e não publicar
  esses assets.
- D54–D60 são dados fictícios aprovados. A homologação de encaixe, aceitação
  de dimensões e cotação no Melhor Envio é pendente de C04/C42; não converter
  os dados simulados em afirmação logística real.
- CAT-Q01 (respondida pelo usuário em 2026-09-21): produtor é entidade
  administrativa sem conta/acesso próprio na primeira versão; somente admin
  gerencia produtores. Não criar portal ou credenciais para produtores na v1.
- CAT-Q02 (respondida pelo usuário em 2026-09-21; D65): produtor referenciado
  não pode ser excluído fisicamente; pode ser desativado para impedir novos
  vínculos/publicações. Produtos referenciados não serão fisicamente removidos;
  sua política detalhada será definida na capacidade de produtos antes de
  expor a operação correspondente. Snapshots e relações históricas persistem.
- CAT-Q03 (respondida pelo usuário em 2026-09-21; D65): exibir apenas
  localidade ampla e texto editorial fictício, ambos claramente rotulados
  como demonstração; não expor coordenadas, endereço ou alegações verificáveis.

CAT-Q01–03 foram aprovadas pelo usuário em 2026-09-21 e registradas em D65.
CAT-Q02/Q03 liberam gestão administrativa de produtores. Para produtos, a
mesma política de não exclusão física é adotada nesta spec: desativação
preserva referências, e snapshots independentes ficam para pedidos.

## 7. Regras e invariantes

1. Todo produto ativo disponível publicamente tem identificador estável, nome, descrição,
   categoria, ao menos um SKU vendável, indicação de conteúdo fictício quando
   for item da demonstração. A oferta pública exige produto e produtor ativos
   e ao menos um SKU ativo; não há estado de publicação separado na v1.
2. SKU é a unidade de venda. Não vender fração de unidade física; quantidade
   do carrinho representa múltiplas unidades do SKU (D17–D18).
3. Categorias comerciais iniciais: quatro alimentos que não exigem
   refrigeração e quatro peças de artesanato (D02, D52–D53). Não inferir
   categoria sanitária ou material não confirmado.
4. Cada SKU é a unidade vendida e guarda rótulo da unidade, conteúdo líquido
   em gramas e dimensões/peso bruto unitários em milímetros/gramas. Para SKU
   de alimento, guardar dias mínimos de validade restantes na chegada
   prevista; esse parâmetro pertence ao SKU e é avaliado com lote/entrega
   fora do catálogo (D17–D18, D27, D54, D57).
5. Artesanato não possui prazo alimentar. Cuia, tigela e vaso são frágeis;
   cesto não é frágil. Chocolate 70% é somente para retirada na v1 (D55–D56).
6. O SKU guarda dimensões brutas unitárias e massa bruta aprovadas em D57;
   gravar cm convertidos exatamente para mm, sem arredondamento. Caixa e
   proteção são responsabilidade de shipping, que consome dimensões e massas
   sem reduzi-las para obter cotação (D35–D37, D58–D60).
7. Valores de preço da demonstração correspondem a D54 e pertencem à
   responsabilidade de pricing. Catálogo não decide promoção, cupom ou total
   do pedido.
8. Produtor e localidade são dados de demonstração fictícios até curadoria e
   aprovação. É proibido associar pessoa real como produtor fictício (D51).
9. Uma mídia publicada referencia origem, licença, autoria/atribuição e
   revisão de enquadramento. Sem evidência, publicação usa alternativa
   tipográfica/ilustração original.
10. Desativar ou alterar um produto não deve modificar snapshots de pedido,
    preço histórico ou descrição adquirida. Produto referenciado é desativado;
    `products` e `product_skus` preservam IDs e relações, e FKs restringem
    exclusão física. Serviços futuros devem copiar atributos adquiridos para
    snapshots independentes, sem cascata do catálogo (CAT-Q02, D65). C21
    protege relações de catálogo; snapshots ainda serão implementados em pedidos.
11. Identidade do SKU é UUID estável e o código legível é único sem distinção
    de caixa. Alteração de produto/embalagem não cria SKU diferente por
    acidente; constraints e validação da aplicação protegem concorrência e
    duplicidade.

## 8. Contratos

Todos os caminhos de produtor abaixo estão versionados no contrato OpenAPI
em C19. Os caminhos de produto permanecem propostos para suas etapas próprias.

As operações de produtor foram aprovadas em C19. O conjunto de rotas de
produto abaixo é o contrato de C22; não criar rotas paralelas se a convenção
existente cobrir o mesmo caso.

| Operação proposta | Acesso | Resultado/erros a documentar |
|---|---|---|
| `GET /api/v1/admin/producers` | admin | página; 401 sessão ausente, 403 papel insuficiente, 400 query inválida |
| `POST /api/v1/admin/producers` | admin + sessão/CSRF | 201; 400 validação, 401, 403, 409 identidade duplicada |
| `GET /api/v1/admin/producers/{id}` | admin | 200; 401, 403, 404 |
| `PATCH /api/v1/admin/producers/{id}` | admin + sessão/CSRF | 200; 400, 401, 403, 404, 409 |
| `DELETE /api/v1/admin/producers/{id}` | não existe | produtor referenciado não é apagado; desativar pelo PATCH |
| `GET /api/v1/admin/products` | admin | página de ativos/inativos; tamanho máximo 50; erros de paginação |
| `POST /api/v1/admin/products` | admin + sessão/CSRF | 201; cria produto e SKUs atomicamente; 400 validação/vínculo, 401, 403, 404 produtor inexistente, 409 slug/SKU duplicado |
| `GET /api/v1/admin/products/{id}` | admin | 200 incluindo SKUs inativos; 401, 403, 404 |
| `PATCH /api/v1/admin/products/{id}` | admin + sessão/CSRF | corpo integral; SKU omitido é desativado, nunca apagado; 400 validação, 401, 403, 404, 409 slug/SKU duplicado |
| `DELETE /api/v1/admin/products/{id}` | não existe | manter produto e SKUs; desativar pelo PATCH |
| `GET /api/v1/products/{slug}` | público | ativo somente se produto, produtor e algum SKU estiverem ativos; 404 caso contrário; DTO editorial não expõe endereço/coordenadas |

Produto ativo define disponibilidade pública neste corte. Não há rota de
publicação separada: ausência de SKU ativo ou produtor ativo também oculta o
produto, e toda alteração administrativa continua exclusiva do admin.

Sem endpoints de upload no primeiro corte: referências de mídia curadas são
configuração/fixture. Cada endpoint deve ter exemplos executáveis para
sucesso e todos os erros aplicáveis, Problem Details com `codigo` e
`correlationId`, limites de paginação e documentação de cookie/CSRF quando
pertinente (D63, `docs/api-guide.md`). Nenhum evento Kafka de catálogo é
necessário nesta etapa; se surgir consumidor assíncrono, sua necessidade e
contrato serão especificados separadamente.

## 9. Critérios de aceite

| ID | Critério | Evidência requerida |
|---|---|---|
| CAT-001 | Modelo distingue produtor, produto e SKU e não invade módulos vizinhos | revisão da spec + testes de fronteira |
| CAT-002 | Oito produtos D52–D60 transcritos sem arredondar/unificar categorias | fixture validada contra `docs/decisions.md` |
| CAT-003 | Procedência e todas as imagens respeitam o limite de ficção/licença | revisão CAT-Q03 e registro de assets C03 |
| CAT-004 | Regras de alimentos/artesanato, retirada, fragilidade e embalagem são explícitas | CAT-009 + `contracts:check` |
| CAT-005 | Contratos administrativos/públicos cobrem autorização, erros e CSRF | OpenAPI + exemplos + cobertura de rotas C19/C20/C22 |
| CAT-006 | Política de remoção preserva referências/snapshots | D65 + constraints/testes C21/C22; snapshots independentes na capacidade de pedidos |
| CAT-007 | Spec revisada pelo usuário antes de C18 | revisão humana registrada no PR |

## 10. Rastreabilidade

| Requisito | Decisão/fonte | Implementação futura | Teste/evidência |
|---|---|---|---|
| Produtor, produto e SKU | D01, D17, D52–D53 | C18–C23 | CAT-001/002 |
| Dados alimentares | D02, D27–D28, D54 | C21 modelo, C26 fixture | CAT-003/009 |
| Unidade vendida, conteúdo, validade e dimensões do SKU | D17–D18, D27, D54, D57 | C21, C41+ | CAT-002/003/004/009 |
| Fragilidade, caixas e proteção | D35–D37, D55–D60 | C21 modelo, C41+ | CAT-004/009 |
| Imagens licenciadas e produtores fictícios | D51, C03/assets | C03/C20+ | CAT-003 |
| API e autorização | D63–D65 | C19–C23 | CAT-005 |
| Preservação de histórico | D30–D31, D34, escopo orders | C19/C20/C50+ | CAT-006 |
