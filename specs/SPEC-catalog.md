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
  `docs/decisions.md` (D52–D60); fixture executável só será criada em C21/C26.
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
| CAT-002 | `ProductSkuIdentityIT` | duplicar SKU ativo conflita; atualizar produto preserva a identidade do SKU e relações | PostgreSQL/Testcontainers |
| CAT-003 | `FoodProductRequirementsTest` | alimento exige unidade/porção e dias mínimos de validade positivos; artesanato não recebe validade alimentar | unitário |
| CAT-004 | `PackagingDimensionsTest` | dimensões/peso ausentes, zero ou negativos são recusados; encaixe não reduz medida por arredondamento | unitário |
| CAT-005 | `ProductImageLicenseTest` | imagem sem fonte, licença ou autoria verificável não pode ser publicada | unitário/validação de fixture |
| CAT-006 | `ProducerAdminApiIT` | sessão/papel/CSRF; admin cria, lista, consulta, edita e desativa produtor; respostas Problem Details correlacionadas | integração HTTP + PostgreSQL |
| CAT-007 | `CatalogPublicContractTest` | produto público não inclui campos administrativos/segredos; resposta corresponde ao schema e exemplos | contrato/HTTP |
| CAT-008 | `CatalogRemovalPolicyTest` | remover/despublicar produto com referências preserva snapshots e integridade; política escolhida antes de implementar exclusão | unitário + PostgreSQL |
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

As respostas CAT-Q02/Q03 liberam gestão administrativa de produtores. A
política de remoção de produto continua definida na especificação de produtos.

## 7. Regras e invariantes

1. Todo produto publicado tem identificador estável, nome, descrição,
   categoria, ao menos um SKU vendável, indicação de conteúdo fictício quando
   for item da demonstração e política de publicação.
2. SKU é a unidade de venda. Não vender fração de unidade física; quantidade
   do carrinho representa múltiplas unidades do SKU (D17–D18).
3. Categorias comerciais iniciais: quatro alimentos que não exigem
   refrigeração e quatro peças de artesanato (D02, D52–D53). Não inferir
   categoria sanitária ou material não confirmado.
4. Alimentos guardam unidade/conteúdo e dias mínimos de validade restantes
   na chegada prevista. Esses dias são uma regra de elegibilidade configurada
   no produto e avaliada com lote/entrega fora do catálogo (D27, D54).
5. Artesanato não possui prazo alimentar. Cuia, tigela e vaso são frágeis;
   cesto não é frágil. Chocolate 70% é somente para retirada na v1 (D55–D56).
6. Produto guarda dimensões brutas unitárias e massa bruta aprovadas em D57.
   Caixa e proteção são responsabilidade de shipping, que consome as
   dimensões e massas sem reduzir dados para obter cotação (D35–D37,
   D58–D60).
7. Valores de preço da demonstração correspondem a D54 e pertencem à
   responsabilidade de pricing. Catálogo não decide promoção, cupom ou total
   do pedido.
8. Produtor e localidade são dados de demonstração fictícios até curadoria e
   aprovação. É proibido associar pessoa real como produtor fictício (D51).
9. Uma mídia publicada referencia origem, licença, autoria/atribuição e
   revisão de enquadramento. Sem evidência, publicação usa alternativa
   tipográfica/ilustração original.
10. Despublicar ou alterar um produto não modifica snapshots de pedido,
    preço histórico ou descrição adquirida. A política de exclusão física
    fica condicionada a CAT-Q02.
11. Identidade do SKU é única. Alteração administrativa não cria outra
    identidade acidentalmente; constraints e validação da aplicação protegem
    concorrência e duplicidade.

## 8. Contratos

Todos os caminhos de produtor abaixo estão versionados no contrato OpenAPI
em C19. Os caminhos de produto permanecem propostos para suas etapas próprias.

As operações de produtor abaixo são aprovadas para C19. As rotas de produto
seguem propostas e exigem especificação própria antes de implementação; não
criar rotas paralelas se a convenção existente cobrir o mesmo caso.

| Operação proposta | Acesso | Resultado/erros a documentar |
|---|---|---|
| `GET /api/v1/admin/producers` | admin | página; 401 sessão ausente, 403 papel insuficiente, 400 query inválida |
| `POST /api/v1/admin/producers` | admin + sessão/CSRF | 201; 400 validação, 401, 403, 409 identidade duplicada |
| `GET /api/v1/admin/producers/{id}` | admin | 200; 401, 403, 404 |
| `PATCH /api/v1/admin/producers/{id}` | admin + sessão/CSRF | 200; 400, 401, 403, 404, 409 |
| `DELETE /api/v1/admin/producers/{id}` | não existe | produtor referenciado não é apagado; desativar pelo PATCH |
| `GET /api/v1/admin/products` | admin | página incluindo rascunhos; erros de paginação |
| `POST /api/v1/admin/products` | admin + sessão/CSRF | 201; 400, 401, 403, 409 SKU duplicado |
| `PATCH /api/v1/admin/products/{id}` | admin + sessão/CSRF | 200; 400, 401, 403, 404, 409 versão/SKU |
| `POST /api/v1/admin/products/{id}/publication` | admin + sessão/CSRF | publicar/despublicar; 400 critérios não satisfeitos, 401, 403, 404, 409 |
| `GET /api/v1/products/{slug}` | público | apenas produto publicado; 404 para indisponível/rascunho |

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
| CAT-005 | Contratos administrativos/públicos cobrem autorização, erros e CSRF | OpenAPI + exemplos + cobertura de rotas C19/C20 |
| CAT-006 | Política de remoção preserva referências/snapshots | decisão CAT-Q02 + testes C19/C20 |
| CAT-007 | Spec revisada pelo usuário antes de C18 | revisão humana registrada no PR |

## 10. Rastreabilidade

| Requisito | Decisão/fonte | Implementação futura | Teste/evidência |
|---|---|---|---|
| Produtor, produto e SKU | D01, D17, D52–D53 | C18–C23 | CAT-001/002 |
| Dados alimentares | D02, D27–D28, D54 | C21, C26+ | CAT-003/009 |
| Dimensões, fragilidade e caixas | D35–D37, D55–D60 | C21, C41+ | CAT-004/009 |
| Imagens licenciadas e produtores fictícios | D51, C03/assets | C03/C20+ | CAT-003 |
| API e autorização | D63–D65 | C19–C23 | CAT-005 |
| Preservação de histórico | D30–D31, D34, escopo orders | C19/C20/C50+ | CAT-006 |
