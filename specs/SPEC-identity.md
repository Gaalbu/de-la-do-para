# SPEC-identity — sessões e contas opcionais

## 0. Metadados

- Módulo: `identity` (de `docs/scope.md` — sem dependências; `checkout`/`cart`/`orders` dependem de `identity`)
- Status: rascunho → em revisão (aprovação humana antes de C15)
- Decisões base: D07 (compra convidada + conta opcional), D33/D34 (cupom por e-mail verificado), D64 (e-mail/senha, sessões PostgreSQL, cookie protegido, CSRF, Mailpit), D61/D62 (monólito modular, monorepo)
- Propostas base: A04 (sessões Spring Security/JDBC + CSRF) — aceita em ADR-0003
- ADRs: `docs/adr/0003-sessoes-cookie-csrf.md`, `docs/adr/0001-monolito-modular.md`
- Personas: visitante/convidado (sem conta), cliente com conta opcional, administrador
- Fonte D64 literal: “Auth: e-mail/senha, Spring Security + sessões PostgreSQL, cookie protegido, CSRF; compra convidada; e-mails no Mailpit”

## 1. Objetivo ◆

Entregar o módulo `identity` como autoridade de autenticação e sessões do monólito: criar contas opcionais, autenticar por e-mail/senha, manter sessões server-side no PostgreSQL, proteger mutações com CSRF e cookie `HttpOnly`/`Secure`/`SameSite`, e isolar compras convidadas de contas — sem exigir conta para comprar, sem vincular pedidos por igualdade de e-mail, e sem expor sessões a cache público.

Fora de escopo desta spec (fica para specs próprias): regras de carrinho (`cart`), snapshot de pedido (`orders`), estoque/preço, frete/entrega/retirada, pagamentos, cupom além da prova de posse, e notificação comercial (essa fica em `notifications`; `identity` só envia verificação/recuperação).

## 2. Comandos ◆

Nada aqui é considerado validado até estar configurado e executado (C09–C13c já entregam os gates). Verificação documental desta spec:

```bash
npm run docs:check --prefix frontend
scripts/verify.sh docs
scripts/verify.sh security
git diff --check
```

Verificação futura da implementação (C15/C76–C81) usará:

```bash
# backend — unit + arquitetura + IT reais (PostgreSQL via Testcontainers)
JAVA_HOME=/home/gaalbu/.sdkman/candidates/java/25.0.4-tem scripts/verify.sh backend
# ou direto:
./backend/mvnw -f backend/pom.xml -B verify -Dtest=SessionSecurityTest,AccountValidationTest
./backend/mvnw -f backend/pom.xml -B verify -Dtest=IdentityPersistenceIT -DfailIfNoTests=false
# contratos
npm run contracts:check --prefix frontend
# frontend + E2E (quando houver UI em C16)
npm run lint --prefix frontend && npm run test:ci --prefix frontend
npm run e2e:local --prefix frontend
```

## 3. Estrutura ◆

```
backend/
  src/main/java/br/com/deladopara/identity/
    domain/
      Account.java              # aggregate: id, email(normalized), passwordHash, emailVerified, role, createdAt
      VerificationToken.java    # token hash + expiry (verificação e recuperação separados)
      Session.java              # mapeada por spring-session-jdbc (tabela SPRING_SESSION)
    application/
      AccountService.java       # casos: register, verifyEmail, authenticate, requestRecovery, resetPassword
      SessionService.java       # consulta/invalidação (logout, expiração)
    adapter/
      web/
        AccountController.java  # bordas HTTP (DTOs imutáveis, Bean Validation)
        SessionController.java  # POST /api/v1/sessions, DELETE /api/v1/sessions/current, GET /api/v1/sessions/current
        CsrfController.java     # GET /api/v1/csrf (se necessário; alternativa: cookie/header duplo)
      persistence/
        AccountRepository.java  # Spring Data JDBC/JPA — só este módulo acessa suas tabelas
        VerificationTokenRepository.java
      mail/
        IdentityMailAdapter.java # Swoosh/Mailpit em dev (não contratar serviço)
  src/main/resources/db/migration/
    V10__identity_accounts.sql
    V11__identity_verification_tokens.sql
    # SPRING_SESSION vem de spring-session-jdbc (V12__spring_session.sql gerado pelo starter)
  src/test/java/br/com/deladopara/identity/
    AccountValidationTest.java
    SessionSecurityTest.java
    IdentityPersistenceIT.java
specs/SPEC-identity.md          # esta spec
contracts/openapi/v1.yaml       # operações de identity (ver §8)
frontend/
  src/app/features/identity/    # C16: login, registro, verificação, recuperação, guarda de rota admin
```

Não criar: `events` próprios além de `AccountCreated`/`EmailVerified`/`PasswordReset` via `eventing` quando C15 for integrado (fora desta spec); abstrações genéricas (`GenericService`); wrappers de `PasswordEncoder` sem benefício; acesso direto ao repository de outro módulo (violação `docs/scope.md` — fronteira `identity` isolada).

## 4. Estilo e convenções ◆

Geral em `docs/contributing.md`. Específico de `identity`:

- E-mail normalizado em minúsculas + trim; unicidade case-insensitive via índice único funcional (`LOWER(email)`); nunca logar e-mail completo em nível INFO sem máscara.
- Senha: 8–72 chars, BCrypt strength 12 (fixado em `application.yml`), `PasswordEncoder` único no módulo; senha nunca retorna em DTO; `char[]` não exigido nesta v1 (String com `eraseCredentials` após auth).
- DTOs imutáveis (`record` Java) nas bordas; Bean Validation (`@Email`, `@NotBlank`, `@Size`); entidade JPA não vaza em HTTP.
- Erros: Problem Details (`application/problem+json`) com `codigo` comercial (`IDENTITY_001`…), `correlationId` de `docs/observability.md`; sem stack trace; sem enumerar existência de e-mail em fluxos públicos quando isso vazaria enumeração (ver §7).
- Dinheiro/datas: não aplicável aqui; datas em UTC (`Instant`), `Clock` injetável para testes; expiração de tokens em minutos configuráveis.
- Cookie de sessão: nome `SESSION`, `HttpOnly=true`, `Secure=true` (exige HTTPS local em C08 — `docs/local-guide.md`), `SameSite=Lax`, `Path=/`, `Max-Age` alinhado à expiração server-side; rotação de ID no login (`changeSessionId`).
- CSRF: `CookieCsrfTokenRepository` com header `X-XSRF-TOKEN`; GET/HEAD/OPTIONS isentos; POST/PUT/PATCH/DELETE exigem token; webhook (`/api/v1/payments/webhooks/*`) isento com auth própria (fora desta spec).

## 5. Estratégia de testes ◆

Cada critério em §9 aponta para teste nomeado. Relógio controlado (`Clock.fixed`) para expiração; sem `Thread.sleep`; sem assert que aceite sucesso e erro como equivalentes.

| Critério | Teste nomeado | Dados iniciais | Gatilho | Asserção | Onde roda |
|---|---|---|---|---|---|
| IDN-001 registro + login | `AccountValidationTest.registerAndLogin` | sem conta | POST /api/v1/accounts (201) → POST /api/v1/sessions (200 + Set-Cookie) | 201, Location, não retorna senha | unit/controller slice |
| IDN-002 e-mail duplicado | `AccountValidationTest.duplicateEmail` | conta `a@b.com` verificada | POST /api/v1/accounts `a@B.com` | 409 IDENTITY_002, não cria segunda linha | unit/IT |
| IDN-003 verificação | `IdentityPersistenceIT.emailVerification` | conta não verificada + token hash expirando em 30m | GET /api/v1/accounts/verify?token=… | 200, `emailVerified=true`, token single-use (segunda tentativa 410) | IT (PG real) |
| IDN-004 token expirado | `IdentityPersistenceIT.expiredToken` | token expirado (-1m) | GET /api/v1/accounts/verify?token=… | 410 IDENTITY_004 | IT |
| IDN-005 login senha errada | `SessionSecurityTest.wrongPassword` | conta verificada | POST /api/v1/sessions senha errada | 401 IDENTITY_005, sem Set-Cookie, sem enumeração | unit |
| IDN-006 sem sessão → 401 | `SessionSecurityTest.noSession` | sem cookie | GET /api/v1/sessions/current | 401 | slice |
| IDN-007 CSRF ausente → 403 | `SessionSecurityTest.csrfMissing` | sessão válida sem header XSRF | POST /api/v1/accounts (ou DELETE session) | 403 | slice (SecurityMockMvc) |
| IDN-008 papel admin | `SessionSecurityTest.adminGuard` | sessão cliente | POST /api/v1/admin/accounts (quando existir) / GET admin-only | 403 IDENTITY_008 | slice |
| IDN-009 logout invalida | `SessionSecurityTest.logoutInvalidates` | sessão válida | DELETE /api/v1/sessions/current → GET /current | 204 + cookie expirado; GET subsequente 401 | IT (session JDBC) |
| IDN-010 recuperação single-use | `IdentityPersistenceIT.passwordReset` | conta verificada | POST /request-recovery → POST /reset com token → reuse | 202 (sempre), depois 200 primeira vez, 410 reuse | IT |
| IDN-011 convidado isolado | `SessionSecurityTest.guestIsolation` | pedido convidado com token | GET /api/v1/orders/:id sem sessão e sem token → com token → com sessão de outro e-mail | 401/403 sem, 200 com token correto, 403 com sessão de e-mail diferente mas igual ao do pedido | IT (integração com orders em C76) |
| IDN-012 rate limit login | `SessionSecurityTest.loginRateLimit` | 5 falhas em 15m no mesmo IP/e-mail | 6ª tentativa | 429 IDENTITY_012 com Retry-After | unit (Bucket4j/Caffeine local, sem infra externa) |

Contratos: `RouteContractCoverageTest` (C11b) já falha se rota sem contrato; `contracts:check` valida exemplos vs schema. E2E (C16) navegará login/logout/expiração no browser.

## 6. Limites de atuação ◆

O que esta spec não decide — pendência bloqueia só a regra dependente, com ID e momento; silêncio nunca é aprovação:

- OAuth/OIDC/2FA/passkeys — fora de v1; não inferir. Se pedido, nova decisão D__ e ADR.
- Cadastro de admin: criação local inicial por comando/seed com senha gerada e impressa no log local (C15), sem senha publicada em repo. Política de convite/admin adicional fica para C76.
- Rate limit exato (valores acima são proposta operacional): 5 tentativas/15m por IP+e-mail para login e 3/h para recovery; confirmar em C15 com teste de limite documentado; sem fechar automaticamente.
- Tamanho mínimo de senha além de 8 e política de complexidade extra pendente de decisão — não escolher silenciosamente.
- Duração de sessão: idle 30m, absoluto 12h (proposta A04) — confirmar em C15; expiração de token verificação 30m, recuperação 15m (mesma regra).
- E-mail de remetente/no-reply e template visual — pendente de C04/brand; usar placeholder `no-reply@deladopara.local` + Mailpit em dev, sem alegar envio real.
- Acesso do admin ao histórico de outros clientes: apenas via papel ADMIN em rotas admin dedicadas; cliente nunca lista pedidos de outro (isolamento por `accountId`).
- Paginação/filtragem de listagens admin — fora desta spec; quando houver, seguir convenção de `contracts/openapi/v1.yaml`.

## 7. Regras e invariantes

### 7.1 Papéis

| Papel | Como obtém | O que pode |
|---|---|---|
| `GUEST` | sem conta/sessão; identificado por `Idempotency-Key` ou token de pedido (hash) | comprar, consultar próprio pedido via token (C76) |
| `CUSTOMER` | registro + verificação opcional; login | comprar, ver próprio histórico em `GET /api/v1/orders` filtrado por `accountId`, usar cupom verificado |
| `ADMIN` | seed local + `ROLE_ADMIN` | gerenciar produtores/produtos (C19/C22), ver todos os pedidos em rotas admin |

Isolamento: igualdade de e-mail entre `GUEST` e `CUSTOMER` nunca vincula pedidos existentes; vínculo explícito futuro exige prova de posse (verificação) e migração opt-in fora desta spec.

### 7.2 Invariantes de persistência

- `accounts.email` único case-insensitive; `CHECK length(email) ≤ 254`; `password_hash` NOT NULL.
- `verification_tokens.token_hash` único; `expires_at` NOT NULL; `used_at` nullable; `type` ∈ {VERIFY, RECOVERY}.
- Sessões em `SPRING_SESSION` (JDBC): `creation_time`, `last_access_time`, `max_inactive_interval` coerentes; expiração server-side prevalece sobre cookie.
- Token em repouso sempre hash SHA-256 (hex) + `BCrypt` não aplicável a token; token em trânsito só via HTTPS; e-mail de verificação contém link com token opaco (não JWT).

### 7.3 Transições

| Origem → Destino | Ator | Precondição | Operação atômica | Evento |
|---|---|---|---|---|
| — → `UNVERIFIED` | GUEST | e-mail/senha válidos, e-mail não existe | INSERT accounts + INSERT verify token + send mail | `AccountCreated` |
| `UNVERIFIED` → `VERIFIED` | GUEST/CUSTOMER | token hash existe, não usado, não expirado | UPDATE accounts.email_verified=true + UPDATE token.used_at | `EmailVerified` |
| `UNVERIFIED` → `LOCKED` | sistema | 5 falhas de login em 15m | UPDATE accounts.locked_until | — |
| `VERIFIED` → `RECOVERY_REQUESTED` | CUSTOMER/GUEST | e-mail existe (resposta 202 sempre, para não enumerar) | UPSERT recovery token + send mail se e-mail existe | — |
| `RECOVERY_REQUESTED` → `VERIFIED` | GUEST/CUSTOMER | token recovery válido, single-use, não expirado | UPDATE password_hash + UPDATE token.used_at | `PasswordReset` |

Sessão: `ANONYMOUS` → `AUTHENTICATED` no `POST /api/v1/sessions` (rotação de ID); `AUTHENTICATED` → `ANONYMOUS` no `DELETE /api/v1/sessions/current` (invalidação); expiração por `maxInactiveInterval` ou `maxLifetime`.

### 7.4 Regras numeradas

- R01: conta opcional — compra não exige `CUSTOMER` nem `VERIFIED`.
- R02: verificação não bloqueia login, mas cupom exige `VERIFIED` (D33) — validado em `pricing`.
- R03: token single-use; segundo uso → 410; timing-safe compare de hash.
- R04: recuperação sempre 202 para não enumerar e-mails; e-mail só enviado se existir.
- R05: CSRF obrigatório em mutações autenticadas; webhook isento.
- R06: cookie `Secure` exige HTTPS local (C08 já expõe via proxy seguro).
- R07: isolamento de histórico — `GET /api/v1/orders` sem `ROLE_ADMIN` filtra por `accountId` da sessão; sem sessão → 401; com token de pedido (C76) autoriza só aquele pedido.
- R08: rate limit local (sem Redis) — 5 login/15m e 3 recovery/h por IP+e-mail; excedido → 429 com `Retry-After`.

## 8. Contratos

Prefixo: `/api/v1`. Auth: `cookie SESSION + X-XSRF-TOKEN` quando indicado. Todos os erros em `application/problem+json` com `codigo`, `mensagem`, `correlationId` (ver `docs/observability.md`). Exemplos completos em `contracts/openapi/v1.yaml` e `contracts/examples/` (mesmo commit da implementação, C15).

### 8.1 HTTP

| # | Método | Caminho | Permissão | Request | Sucesso | Erros |
|---|---|---|---|---|---|---|
| I-01 | POST | `/api/v1/accounts` | público + CSRF se sessão existe | `{email, password}` | 201 Created + `Location: /api/v1/accounts/{id}` + `{id,email,emailVerified:false}` | 400 (validação), 409 IDENTITY_002 duplicado (case-insensitive), 403 CSRF, 429 |
| I-02 | POST | `/api/v1/accounts/verify` | público | `{token}` ou `GET /verify?token` com redirect 303 para frontend | 200 `{emailVerified:true}` | 400 token ausente, 410 expirado/usado |
| I-03 | POST | `/api/v1/sessions` | público (login) | `{email,password}` + CSRF se já há sessão | 200 `{id,email,role,emailVerified}` + `Set-Cookie: SESSION=…` + `X-XSRF-TOKEN` | 400 validação, 401 IDENTITY_005 credenciais, 423 locked, 403 CSRF, 429 |
| I-04 | GET | `/api/v1/sessions/current` | autenticado | — | 200 `{id,email,role,emailVerified}` | 401 |
| I-05 | DELETE | `/api/v1/sessions/current` | autenticado + CSRF | — | 204 + `Set-Cookie: SESSION=; Max-Age=0` | 401, 403 CSRF |
| I-06 | POST | `/api/v1/accounts/recovery` | público | `{email}` | 202 (sempre) | 400 validação, 403 CSRF (se sessão), 429 |
| I-07 | POST | `/api/v1/accounts/reset` | público | `{token,newPassword}` | 200 | 400 validação, 410 token, 403 CSRF, 429 |
| I-08 | GET | `/api/v1/csrf` | público | — | 200 `{token}` + `Set-Cookie: XSRF-TOKEN=…` (se usar CookieCsrfTokenRepository) | — |

Notas:

- I-01 e I-03 validam e-mail com `jakarta.validation` e normalizam antes de persistir; senha 8–72, sem log.
- I-02 aceita POST JSON (API) e GET com query (link do e-mail) — ambos com mesma lógica; GET não muta sem token válido? Muta, mas é idempotente single-use e exige token imprevisível (128-bit).
- I-03 rota de login não exige CSRF quando ainda não há sessão; mas se houver sessão, exige (evita login CSRF).
- Paginação não aplicável; `Idempotency-Key` não exigido aqui (aplicável a checkout/payments).
- Eventos: `AccountCreated` (após I-01), `EmailVerified` (I-02), `PasswordReset` (I-07) publicados via `eventing` Outbox quando o módulo for integrado em C15 — fora desta spec o contrato de evento é só referência; não afirmar entrega Kafka nesta etapa.

### 8.2 Exemplos

Ver `contracts/examples/identity-register-201.json`, `identity-login-200.json`, `problem-401.json` (a criar em C15). Stub WireMock em `infra/wiremock/mappings/identity-verify-200.json` para `docs/api-guide.md` quando a spec for aprovada.

Checklist API-E-TESTES D63 por operação: propósito, permissões, parâmetros com limites, schemas, exemplos de sucesso e de **todas** as respostas de erro aplicáveis (401/403/409/410/423/429), status, cabeçalhos `Set-Cookie`/`X-XSRF-TOKEN`/`Retry-After`, convenções de datas (`Instant` ISO-8601), Problem Details com `codigo`+`correlationId`, e sessão/cookie/CSRF por papel (GUEST/CUSTOMER/ADMIN + 401/403). Categorias não aplicáveis justificadas (ex.: 404 não se aplica a `/sessions/current` — usa 401).

## 9. Critérios de aceite

| ID | Critério | Teste(s) | Evidência |
|---|---|---|---|
| IDN-001 | Registro cria conta UNVERIFIED e login estabelece sessão com cookie protegido | `AccountValidationTest.registerAndLogin` + `IdentityPersistenceIT` | relatório `verify` + SHA + `Set-Cookie` com `HttpOnly; Secure; SameSite=Lax` |
| IDN-002 | E-mail duplicado case-insensitive nega com 409 | `AccountValidationTest.duplicateEmail` | relatório + resposta Problem Details IDENTITY_002 |
| IDN-003 | Verificação com token válido marca VERIFIED e é single-use | `IdentityPersistenceIT.emailVerification` | relatório IT PG real + segunda tentativa 410 |
| IDN-004 | Token expirado retorna 410 | `IdentityPersistenceIT.expiredToken` | relatório (Clock fixo) |
| IDN-005 | Senha incorreta retorna 401 sem Set-Cookie | `SessionSecurityTest.wrongPassword` | relatório slice |
| IDN-006 | Acesso sem sessão retorna 401 | `SessionSecurityTest.noSession` | relatório |
| IDN-007 | Mutação sem CSRF retorna 403 | `SessionSecurityTest.csrfMissing` | relatório |
| IDN-008 | Papel CUSTOMER sem ADMIN não acessa rota admin | `SessionSecurityTest.adminGuard` | relatório 403 IDENTITY_008 |
| IDN-009 | Logout invalida sessão (subsequente 401) | `SessionSecurityTest.logoutInvalidates` | relatório IT session JDBC |
| IDN-010 | Recuperação single-use: 202 sempre, reset 200 primeira, 410 reuse | `IdentityPersistenceIT.passwordReset` | relatório + Mailpit `http://localhost:8025` |
| IDN-011 | Histórico isolado: GUEST sem token 401/403; com token correto 200 só daquele pedido; sessão de outro e-mail não lista alheio | `SessionSecurityTest.guestIsolation` (preparado para C76) | relatório (quando orders existir; nesta spec só contrato) |
| IDN-012 | Rate limit login 5/15m retorna 429 com Retry-After | `SessionSecurityTest.loginRateLimit` | relatório |
| IDN-013 | `RouteContractCoverageTest` passa: rotas de identity documentadas em `contracts/openapi/v1.yaml` | `RouteContractCoverageTest` + `contracts:check` | CI 7/7 verde |

Evidência final: `docs/evidence/c14/` com `verify` + `contracts:check` + link para PR aprovado. Licença/destino de releases e remetente de e-mail seguem em pendências gerais (`docs/decisions.md:103`).
