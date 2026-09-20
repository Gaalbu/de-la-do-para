# Contribuindo — De Lá do Pará (SDD)

Guia operacional criado em C02. Fonte: `docs/PLANO-MESTRE.md` (§7 SDD, §8
commits, §9 verificação). Nomes e código em inglês; guias e textos da loja
em pt-BR.

## Fluxo SDD por capacidade

1. **Mapa** (`docs/scope.md`): revisar limites, dependências e ordem.
2. **Specify**: escrever `specs/SPEC-<module-id>.md` a partir de
   [`spec-template.md`](spec-template.md), com critérios identificados
   (`CHK-001`, `PAY-004`, `INV-003`…). Revisão humana aprova a spec antes do
   código quando ela introduz decisões ainda não aprovadas (D01–D64 não se
   perguntam de novo).
3. **Plan**: abordagem, alternativas em ADR ([`adr-template.md`](adr-template.md)),
   risco, arquivos previstos, estratégia de verificação.
4. **Tasks**: unidades independentes, um commit proposto cada, até três
   critérios principais e comando de verificação.
5. **Implement**: fatias verticais; regra comercial nasce em teste que falha
   pelo motivo esperado, depois o mínimo para passar.
6. **Verify/Validate**: contratos e invariantes (verificação) + jornada no
   navegador/sandbox (validação).
7. **Record**: ligar requisito → spec → teste → commit → evidência em
   [`traceability.md`](traceability.md). Mudança de regra atualiza spec/ADR
   primeiro.

**Sempre:** validar entradas nas bordas; transações curtas; caminho de erro
relevante; proteger segredos; atualizar a documentação que mudou.
**Perguntar:** regra comercial ambígua, nova dependência arquitetural, custo/
provedor, exposição externa ou ampliação de escopo — uma de cada vez, com
opções e recomendação.
**Nunca:** mudar requisito para teste passar; desabilitar teste sem decisão;
apresentar mock como integração real; suprimir erro; confirmar pagamento por
parâmetro do browser.

## Verificação vs validação

- **Verificação:** a implementação respeita contrato, transição e invariante
  (testes, gates, contratos).
- **Validação:** comprador/admin concluem o fluxo e compreendem o resultado,
  incluindo erro e recuperação (navegador, sandbox, outra pessoa executando
  os exemplos).

## Política de commits atômicos

- Uma intenção verificável por commit; teste e correção da mesma intenção no
  mesmo commit. Nunca publicar commit que quebra a `main`.
- Até ~5 arquivos manuais por tarefa (bootstrap gerado é exceção registrada).
- Separar: formatação em massa, bump de dependência, mudança comercial.
  Migration pertence à mudança que depende dela.
- Conventional Commits com escopo: `feat(inventory): reserve stock atomically`.
  Tipos: `feat`, `fix`, `test`, `docs`, `refactor`, `build`, `ci`, `chore`.
- Antes do commit: revisar diff, rodar checks pertinentes, conferir ausência
  de segredos, confirmar o critério de aceite. Stagear arquivos pertinentes
  (evitar `git add .`). Sem rodapé `Co-Authored-By` de IA.
- PR pequeno: objetivo, critérios, docs alteradas, comandos/resultados,
  screenshots de UI, riscos. Sem merge automático; sem reescrever histórico
  público sem autorização.

## Convenções de código (resumo; detalhe no plano §7)

- Backend: constructor injection, DTOs imutáveis (entidades JPA não vazam em
  HTTP), Bean Validation nas bordas, `Clock` injetável, dinheiro sem `double`,
  erros Problem Details com código comercial + correlationId, sem stack trace.
- Frontend: standalone, strict, Signals + `computed`, RxJS só para I/O,
  formulários tipados, estados explícitos (carregando/vazio/erro/sucesso),
  idempotência no servidor (botão desabilitado é só UX).
- Docs de endpoint: método, caminho, permissão, parâmetros, schemas, exemplos
  de sucesso/erro e status — sempre no mesmo commit do comportamento.
