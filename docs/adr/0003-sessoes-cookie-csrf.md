# ADR-0003: sessões JDBC + cookie protegido + CSRF

- Data: 20/09/2026
- Status: aceito (D64)
- Decisores: usuário (D64) + executor

## Problema

Conta opcional + compra convidada + admin no mesmo backend, com SSR, sem
vazar sessão por cache compartilhado e sem JWT auto-contido difícil de
revogar.

## Alternativas consideradas

| Alternativa | Prós | Contras / custo operacional |
|---|---|---|
| JWT stateless | sem storage | revogação/expiração imediata difíceis; risco de dados em token |
| OAuth2 próprio completo | padrão | excesso para loja de demonstração |
| **Sessões Spring Security/JDBC + cookie + CSRF (escolhida)** | revogação imediata, login por e-mail/senha, convidado isolado por sessão | exige HTTPS local p/ cookie Secure; CSRF em mutações; storage de sessão |

## Decisão

Spring Security com sessões persistidas no PostgreSQL, cookie HttpOnly/
Secure/SameSite, rotação no login, CSRF nas mutações; exceção de CSRF só na
rota de webhook (autenticação própria por token). Pedido de convidado via
sessão de compra ou link com token aleatório (hash + expiração); igualdade
de e-mail nunca vincula pedido. Verificação/recuperação no Mailpit.
HTTPS de desenvolvimento no ambiente local para exercitar Secure (C08).

## Consequências

SSR público sem dados de sessão; rotas privadas sem cache público; testes
de isolamento entre identidades (C85); rate limit em login/recuperação/
checkout/links.

## Evidência

D64 em `docs/decisions.md`; spec futura `SPEC-identity.md` (C14).
