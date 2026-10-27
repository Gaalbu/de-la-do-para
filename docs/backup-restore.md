# Backup e restauração do banco — De Lá do Pará

Procedimento local para copiar o PostgreSQL e conferir a cópia em um banco
separado (C93). Ele nunca sobrescreve o banco em uso: a volta de um backup
antigo ao banco principal exige conciliação de pagamentos, reembolsos e
etiquetas e pertence à C94.

## O que os testes provam

`DatabaseUpgradeAndRestoreIT` executa três cenários, cada um em um banco
próprio dentro de um PostgreSQL 18.6 descartável (Testcontainers):

| Cenário | Verificação |
|---|---|
| Banco vazio | Todas as migrations aplicam até a versão mais recente; nada fica pendente e `validate` passa. |
| Atualização com dados | Um banco migrado até a V30 recebe dados fictícios (`db/snapshots/v30-seed.sql`: conta, produtor, produtos, preços, lotes, pedido, item, intenção de pagamento e evento de outbox) e é migrado até a versão atual. As chaves de todas as linhas e os valores de pedido, pagamento e estoque continuam iguais. |
| Restauração isolada | O banco atualizado é copiado com `pg_dump -Fc` e restaurado com `pg_restore --exit-on-error` em outro banco. Contagem de linhas por tabela, constraints, índices e histórico do Flyway coincidem; a cópia não tem migrations pendentes e a origem fica intacta. |

```bash
./backend/mvnw -B -f backend/pom.xml -Dit.test=DatabaseUpgradeAndRestoreIT verify
```

Os dados do seed são fictícios. O snapshot é gerado pelas próprias migrations
até a V30; não há dump binário versionado.

## Backup do ambiente local

Com o profile `local` do Compose em execução (contêiner `dlp-postgres`):

```bash
mkdir -p backups
docker exec dlp-postgres pg_dump -U deladopara -d deladopara -Fc \
  > "backups/deladopara-$(date +%Y%m%dT%H%M%S).dump"
tar -czf "backups/media-$(date +%Y%m%dT%H%M%S).tar.gz" -C data media
```

Ajuste usuário e banco se `POSTGRES_USER`/`POSTGRES_DB` forem diferentes no
`.env`. A pasta `backups/` contém dados pessoais fictícios ou reais do ambiente
local: não a versione nem a compartilhe. As imagens de produto ficam em
`APP_MEDIA_DIRECTORY` e não estão no dump.

## Conferir a cópia em um banco separado

```bash
docker exec dlp-postgres createdb -U deladopara deladopara_restore_check
docker exec -i dlp-postgres pg_restore -U deladopara -d deladopara_restore_check \
  --no-owner --exit-on-error < backups/<arquivo>.dump
docker exec dlp-postgres psql -U deladopara -d deladopara_restore_check -c \
  "SELECT version, success FROM flyway_schema_history ORDER BY installed_rank DESC LIMIT 1"
docker exec dlp-postgres dropdb -U deladopara deladopara_restore_check
```

A última versão e `success = t` devem coincidir com o banco de origem. O banco
`deladopara_restore_check` serve só para conferência; a API e o worker
continuam apontando para `deladopara`.

## Limites

- O procedimento cobre um único PostgreSQL local. Não há backup contínuo, WAL
  archiving nem retenção automática.
- Tópicos Kafka não entram no backup. Eventos ainda não publicados continuam na
  outbox do banco; o estado de consumidores após uma restauração é tratado na
  C94.
- Restaurar sobre o banco principal com pedidos e pagamentos posteriores ao
  backup pode desfazer fatos confirmados pelos provedores. Não fazer isso antes
  do runbook da C94.
