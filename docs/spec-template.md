# Template de spec de módulo

Copie para `specs/SPEC-<module-id>.md` e preencha. Ver exemplo preenchido em
[`spec-example.md`](spec-example.md). A spec é aprovada por revisão humana
antes da implementação do módulo.

> As seis áreas obrigatórias estão marcadas com ◆. Critérios usam IDs
> estáveis (`<PREFIXO>-<NNN>`) e cada um aponta para testes e evidências.

## 0. Metadados

- Módulo: `<module-id>` (de `docs/scope.md`)
- Status: rascunho | em revisão | aprovada
- Decisões base: D__… (aprovadas); propostas A__… (a validar em ADR)
- Personas: …

## 1. Objetivo ◆

O que a capacidade entrega e para quem, em 1–2 parágrafos. Fora de escopo
explícito.

## 2. Comandos ◆

Comandos exatos para verificar a capacidade (alvos futuros do plano §10,
ex.: `./backend/mvnw -f backend/pom.xml -Dtest=NomeTest test`).
Nada aqui é considerado validado até ser configurado e executado (C06–C13).

## 3. Estrutura ◆

Arquivos previstos (pacotes `B/<módulo>`, testes `T/<módulo>`, `F/<feature>`,
`M/` migrations), contratos em `contracts/`, e o que NÃO criar (sem camadas
genéricas de uso único, sem wrappers sem benefício).

## 4. Estilo e convenções ◆

Convenções específicas do módulo (nomenclatura de enums, formato de erros,
padrões de DTO). O geral está em [`contributing.md`](contributing.md).

## 5. Estratégia de testes ◆

Para cada critério: teste nomeado, dados iniciais, gatilho, asserção e onde
roda (unitário / Testcontainers / contrato / E2E). Relógio controlado para
datas; sem sleeps arbitrários; sem asserts que aceitam sucesso e erro como
equivalentes. Regras de dinheiro, datas, cupons e embalagem testam o limite
exato e ambos os lados dele.

## 6. Limites de atuação ◆

O que a spec não decide (pendências bloqueiam só a regra dependente, com ID
da pergunta e momento de resolução). Silêncio nunca é aprovação.

## 7. Regras e invariantes

Regras numeradas + invariantes protegidas por domínio e constraints de banco.
Transições de estado como tabela origem → destino → ator → precondição →
operação atômica → evento.

## 8. Contratos

Operações HTTP (método/caminho/permissão/schemas/exemplos de sucesso e erro) e eventos
(producer/consumidor/versão/payload/correlação/dedup/retry/quarentena/replay).
Toda rota implementada tem contrato; contrato inválido falha no gate.

Checklist API-E-TESTES (D63) herdado por cada operação: propósito,
permissões, parâmetros com obrigatoriedade e limites, schemas, exemplos
executáveis de requisição e de **todas** as respostas de erro aplicáveis,
status, cabeçalhos (incluindo `Idempotency-Key` quando aplicável),
convenções de dinheiro/datas/paginação, erros Problem Details com `codigo` e
`correlationId`, e documentação de sessão/cookie/CSRF com exemplos por papel
(convidado/cliente/admin) e 401/403. Justificar por escrito quando uma
categoria de erro não se aplicar.

## 9. Critérios de aceite

| ID | Critério | Teste(s) | Evidência |
|---|---|---|---|
| `<MOD>-001` | … | `NomeTest` / `NomeIT` | relatório + SHA |
