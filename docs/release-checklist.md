# Checklist de release da v1 (C97)

Estado verificado em 2026-11-10 sobre `main@0a9b5c5`. Cada linha cita a
evidência; o que não foi executado aparece como lacuna, não como pendência
escondida. Escopo: [PLANO-MESTRE.md](PLANO-MESTRE.md). Arquitetura e testes por
falha: [architecture.md](architecture.md).

## Provado

| Item | Evidência |
|---|---|
| CI verde no `main` (backend, frontend, contratos, docs, segurança, commit-policy, quality-gate) | Run `success` em `0a9b5c5` (após #177) |
| Cenários de falha de checkout, pagamento, eventos e reembolso | 23 linhas da matriz em `architecture.md`, cada uma com teste de integração em PostgreSQL real |
| Migrations em banco vazio e sobre dados antigos; restauração isolada | `DatabaseUpgradeAndRestoreIT` 3/3 ([backup-restore.md](backup-restore.md)) |
| Imagens de API, worker e frontend sem root, sem segredos, com healthcheck | `scripts/release-smoke.sh` em clone limpo de `0a9b5c5`: 29 migrations, API/worker/frontend saudáveis, mesma imagem para API e worker |
| `docs:check` e `contracts:check` a partir de clone limpo | Mesma sessão do smoke |
| Contrato HTTP e eventos sem drift | `RouteContractCoverageTest`, `contracts:check`, `check-events` |
| Páginas privadas sem cache compartilhado e sem eco do cookie | `session-isolation.spec.ts` |
| Rotas principais sem violação automática WCAG 2.1 A/AA | `a11y.spec.ts` (`npm run e2e:a11y`) |
| Sem vulnerabilidade conhecida em dependências | Gate `security` (npm audit + osv-scanner) verde |

## Não provado nesta versão

| Lacuna | Por quê | O que fecharia |
|---|---|---|
| Pagamento e frete em sandbox real | Sem credenciais Asaas/Melhor Envio (C04); adapter Asaas só com HTTP gravado | Roteiro SB de [homologation.md](integrations/homologation.md) |
| Orçamentos de desempenho | `k6` indisponível; só existe a carga de catálogo (#167), nunca executada | C90 completo e C91 |
| Jornada de compra sobre as imagens de release | Falta gateway para `/api` (ver [release.md](release.md)) | Compose de release com gateway |
| API subindo a partir do README em clone limpo | O Compose usa nomes fixos e colide com uma stack local já existente | Rodar em máquina sem a stack, ou parametrizar os nomes |
| Teclado e contraste em conteúdo real | O axe cobre só regras automáticas | Revisão manual |
| Vídeo de portfólio | Depende de gravação e aprovação (C96a/C96b) | [storyboard.md](design/storyboard.md) |
| Etiquetas, rastreio, notificações, UI de conta e de cupons | Fora da v1 por decisão de escopo | Evolução futura |

## Release local (C98)

Não há publicação em registro nem deploy. A release local é: o commit de `main`,
`scripts/release-smoke.sh` (imagens `0.0.0-local`) e este checklist. Marcar a
tag `v0.1.0` só depois que as lacunas acima forem aceitas ou fechadas pelo
Manito.
