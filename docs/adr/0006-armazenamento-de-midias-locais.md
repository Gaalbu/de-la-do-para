# ADR 0006: armazenar mídia do catálogo em arquivos locais persistentes

- Status: aceita para o escopo local do projeto
- Data: 2026-09-21
- Contexto: C24 precisa persistir uma imagem principal por produto, sem custo de serviço externo, com operação local reproduzível.

## Decisão proposta

Guardar bytes validados em diretório local configurável e persistente (`APP_MEDIA_DIRECTORY`, padrão `./data/media`) porque o backend atualmente roda no host. PostgreSQL guarda somente a associação e os metadados. O adapter recebe IDs gerados pelo servidor, nunca nomes/caminhos fornecidos pelo navegador. Se o backend for containerizado, montar esse caminho em volume próprio do Compose. A porta de armazenamento pertence a `catalog`; nenhum provider de nuvem será instalado nesta etapa.

## Alternativas

- Bytes em PostgreSQL: transação simples, porém mistura blobs de imagem ao banco e aumenta backup/restore sem necessidade para o portfólio local.
- URLs remotas: elimina armazenamento próprio, mas cria dependência externa e risco de SSRF, indisponibilidade e alteração de conteúdo.
- Object storage: adiciona serviço e configuração sem necessidade demonstrada no ambiente local e de custo zero aprovado.

## Consequências e controles

- Backups locais devem incluir o volume de mídia junto ao snapshot PostgreSQL; restaurar só um deles pode deixar associações quebradas ou arquivos órfãos.
- Escrita em arquivo e commit SQL não formam transação distribuída. Escrever primeiro em chave nova, trocar a referência no banco e só depois remover o arquivo anterior; falha não pode invalidar a imagem publicada anterior.
- Desenvolvimento/testes apontam para diretórios próprios; CDN e limpeza automática global não integram esta decisão.
- Spring Boot oferece limites multipart configuráveis e usa a API Servlet; o projeto também valida conteúdo, dimensões e nome antes de servir.

## Referências

- <https://docs.spring.io/spring-boot/how-to/spring-mvc.html#howto.spring-mvc.multipart>
- <https://docs.spring.io/spring-framework/reference/web/webmvc/mvc-controller/ann-methods/multipart-forms.html>
