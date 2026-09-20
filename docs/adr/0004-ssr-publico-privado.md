# ADR-0004: SSR em páginas públicas, privado sem cache compartilhado

- Data: 20/09/2026
- Status: proposto (valida em C07 com teste de duas identidades)
- Decisores: executor (aprovação do usuário na revisão)

## Problema

Vitrine indexável e LCP ≤2,5 s (D41) sem expor dados de um cliente a outro
via HTML cacheado.

## Alternativas consideradas

| Alternativa | Prós | Contras / custo operacional |
|---|---|---|
| SPA puro | simples | SEO/LCP piores; fora da direção D32/D41 |
| SSR total com cache | rápido | risco de vazar sessão entre usuários |
| **SSR/hidratação só no público + privado sempre dinâmico (escolhida)** | catálogo/produto/produtor renderizados; conta/carrinho/admin isolados | camada SSR acessa só endpoints necessários; custo de SSR medido (C91) |

## Decisão

Produto, produtor e catálogo com SSR/hidratação; carrinho, conta e admin em
renderização privada sem cache compartilhado; cliente HTTP do SSR restrito
aos endpoints públicos; locale pt-BR/BRL.

## Consequências

Teste com duas identidades simultâneas (C85); medição de LCP/CLS em
laboratório documentado; custo de SSR entra no orçamento de recursos (C79a).

## Evidência

A05 em `docs/decisions.md`; metas D41; spec futura `SPEC-storefront.md`
(C33); protótipo/fluxos em `docs/design/brief.md`.
