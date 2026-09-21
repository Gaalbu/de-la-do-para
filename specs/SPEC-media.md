# Spec de mídia de catálogo — C24

## 0. Metadados

- Módulo: `catalog` (subcapacidade `media`)
- Status: em implementação; o usuário aprovou uma imagem principal por produto e JPEG/PNG até 5 MiB em 2026-09-21
- Decisões base: D51, D61–D64; `SPEC-catalog.md` CAT-003/CAT-005
- ADR relacionado: `docs/adr/0006-armazenamento-de-midias-locais.md`

## 1. Objetivo

Permitir que administradores associem uma imagem principal licenciada a cada produto de demonstração, com texto alternativo, autoria e atribuição. A imagem deve sobreviver a reinícios da aplicação local e só pode ser servida como conteúdo de imagem validado. A loja não busca URLs fornecidas por usuários.

O corte não cria galeria, edição de pixels, busca por URL, CDN, processamento assíncrono, nem cópia de imagem para pedido. Imagem é opcional; produto sem imagem continua visível e pode usar o cartão tipográfico aprovado em C03.

## 2. Comandos de verificação

```bash
./backend/mvnw -B -f backend/pom.xml -Dit.test=CatalogMediaApiIT verify
npm --prefix frontend run contracts:check
npm --prefix frontend run docs:check
./scripts/verify.sh security
./scripts/verify.sh frontend
git diff --check
```

Os testes de API/persistência usam PostgreSQL/Testcontainers e diretório temporário isolado; o teste de armazenamento abre os mesmos arquivos por uma segunda instância do adapter. Playwright comprova o fluxo de upload, prévia, substituição/remoção e o `alt` resultante no caminho público. O sandbox de E2E usa respostas controladas; não substitui testes HTTP e de arquivo reais.

## 3. Estrutura e fronteiras

- `backend/catalog`: entidade/metadados, validação, serviço e adapter de armazenamento local configurado por `APP_MEDIA_DIRECTORY`.
- `backend/db/migration`: migration para uma imagem por produto e metadados de autoria, licença, atribuição e `alt`.
- `contracts/openapi/v1.yaml`: upload multipart administrativo, remoção, descriptor opcional nos DTOs e entrega pública da imagem.
- `frontend/features/admin/products`: upload e prévia da imagem principal, campos de descrição/licença e estado acessível.
- Guia local: diretório persistente configurado por `APP_MEDIA_DIRECTORY` (padrão `./data/media`) para o backend executado no host; quando o backend for containerizado, esse caminho deve ser montado em volume próprio. Testes usam `@TempDir` e não escrevem nos dados do usuário.

Não persistir bytes no PostgreSQL, nome original do arquivo ou URLs externas como fonte de imagem. Não adicionar abstração de storage multi-provider ou dependência de upload se as APIs Servlet existentes forem suficientes.

## 4. API proposta

- `PUT /api/v1/admin/products/{productId}/image`: admin + sessão/CSRF; multipart com `file`, `altText`, `source`, `license` e `rightsReviewed=true`; `creator` e `attribution` são opcionais. Cria ou substitui a imagem principal. Primeira associação retorna 201; substituição, 200.
- `DELETE /api/v1/admin/products/{productId}/image`: admin + sessão/CSRF; remove associação e arquivo. É permitido remover mídia, não o produto.
- `GET /api/v1/product-images/{imageId}`: público; responde somente bytes validados e associados a produto, produtor e ao menos um SKU ativos; 404 nos demais casos.
- `GET /api/v1/products/{slug}` e DTO administrativo incluem descriptor opcional `{ url, altText }`; licença/autoria ficam restritas à administração.

Falhas usam Problem Details com `codigo` e `correlationId`: sessão ausente, papel/CSRF insuficiente, produto inexistente, arquivo vazio/corrompido, formato não aceito, dimensões excessivas, conteúdo acima do limite e dados de licença/alt inválidos. Nenhuma resposta inclui caminho local, stack trace ou nome original enviado pelo cliente.

## 5. Regras de segurança e conteúdo

1. Permitir apenas JPEG e PNG estáticos, confirmados pelo conteúdo e por decodificação real; não confiar no `Content-Type` nem na extensão enviados. Rejeitar SVG, conteúdo truncado e arquivo de outro formato disfarçado.
2. Máximo aprovado para o arquivo comprimido: 5 MiB; requisição multipart limitada a 6 MiB. Limites de processamento: 12 megapixels e até 6000 px por dimensão. Impor tamanho no parser multipart antes de alocar buffers da aplicação; não aceitar tamanho ilimitado.
3. Re-encodar pixels decodificados para o formato permitido antes de gravar, descartando metadados EXIF que poderiam divulgar localização pessoal.
4. Gerar UUID aleatório interno; ignorar caminho/nome original, validar o diretório de destino e criar arquivos sem sobrescrita acidental. Substituição grava arquivo novo antes de trocar os metadados.
5. Armazenar fora da árvore estática do Angular. A rota pública valida vínculo e estado do produto; `Content-Type` vem do formato detectado, com `X-Content-Type-Options: nosniff` e sem interpretar HTML/SVG.
6. Exigir `altText` descritivo (1–250 caracteres), origem e licença. Autoria e atribuição podem ser registradas quando fornecidas. O administrador confirma que conferiu licença, autoria e enquadramento conforme D51; o sistema registra essa confirmação sem buscar a origem remota (evita SSRF).
7. Imagem só é pública quando produto, produtor e ao menos um SKU estão ativos. Desativar qualquer um oculta a rota sem perder a imagem associada.
8. Falha de validação deixa imagem e metadados atuais intactos. Substituição remove bytes antigos somente depois de persistir a nova associação; falha de remoção é registrada para limpeza segura, sem quebrar a URL nova. A aplicação roda no host atualmente; diretório persistente é configurável. Um volume dedicado passa a ser necessário se o backend virar serviço Compose.

## 6. Estilo e convenções

- Java/TypeScript em inglês; interface/documentação da loja em pt-BR.
- UUIDs independentes do nome do arquivo e do produto.
- Caminhos são construídos a partir do ID gerado pelo servidor, nunca do nome recebido. Metadados públicos têm allowlist própria.
- Endpoints mutáveis seguem as convenções existentes de sessão, papel admin, CSRF, Problem Details e correlação; autorização da UI nunca substitui a do backend.

## 7. Critérios de aceite

| ID | Critério | Evidência |
|---|---|---|
| MED-001 | No máximo uma imagem principal por produto; nova imagem substitui a anterior sem perder a vigente em caso de erro | constraint/repositório e `CatalogMediaApiIT` |
| MED-002 | Somente admin autenticado com CSRF pode enviar/remover | integração HTTP 401/403/CSRF |
| MED-003 | MIME/extensão forjados, SVG, conteúdo corrompido, traversal, pixels acima do limite e payload grande são rejeitados | testes de abuso com `@TempDir` e `CatalogMediaApiIT` |
| MED-004 | Nome interno é aleatório; nenhum byte fica acessível sob diretório estático ou caminho arbitrário | teste adapter/rota pública |
| MED-005 | Arquivo e descrição sobrevivem a reinício/novo adapter usando o volume persistente | teste de adapter e roteiro local executável |
| MED-006 | Produto/produtor/SKUs inativos não expõem bytes; produto ativo retorna descriptor com `altText` | integração API + Playwright |
| MED-007 | Origem, licença e confirmação D51 são obrigatórias; autoria e atribuição são registradas quando fornecidas; sem fetch de URL | validação e contrato |
| MED-008 | Admin consegue carregar, pré-visualizar, substituir e remover imagem sem perder cadastro do produto | Playwright |
| MED-009 | Contratos, guia API/local, matriz e gates refletem a implementação | `contracts:check`, `docs:check`, CI |

## 8. Decisões aprovadas e limites do corte

- O usuário aprovou JPEG/PNG até 5 MiB e uma imagem principal substituível por produto em 2026-09-21.
- Limites de decodificação (12 MP/6000 px) são controles técnicos para limitar uso de memória e CPU; podem ser revistos se a curadoria trouxer imagens legítimas que não caibam neles.
- A galeria permanece fora deste corte.

## 9. Referências técnicas verificadas

- Spring Boot 4.1 documenta limites multipart por arquivo e por requisição, configuráveis por propriedades: <https://docs.spring.io/spring-boot/how-to/spring-mvc.html#howto.spring-mvc.multipart>.
- Spring Framework 7 documenta `MultipartFile` e suporte Servlet multipart: <https://docs.spring.io/spring-framework/reference/web/webmvc/mvc-controller/ann-methods/multipart-forms.html>.
- Java SE 25 documenta `ImageIO` e a decodificação/re-encodificação raster usada para validar os bytes: <https://docs.oracle.com/en/java/javase/25/docs/api/java.desktop/javax/imageio/ImageIO.html>.
- Política de conteúdo e atribuição do projeto: D51 e `docs/design/assets.md`.
