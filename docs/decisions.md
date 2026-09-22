# Registro de decisões — De Lá do Pará

Transcrição fiel das decisões aprovadas pelo usuário (D01–D67), das propostas
técnicas em revisão (A01–A10) e das perguntas com encaminhamento. Fonte:
`docs/PLANO-MESTRE.md` (consolidação 20/09/2026). Quando houver sobreposição,
a decisão mais recente e específica prevalece. Não repetir perguntas já
respondidas em D01–D65.

## Decisões confirmadas pelo usuário

| ID | Decisão |
|---|---|
| D01 | Loja única de produtos paraenses, com procedência, produtores, entrega e retirada |
| D02 | Alimentos que não exigem refrigeração e artesanato |
| D03 | Projeto de portfólio, inicialmente local, sem custo de serviços |
| D04 | Implementação do zero, com identidade própria; projeto LAPES apenas como referência |
| D05 | Spring Boot e Angular |
| D06 | Foco técnico em checkout confiável com Kafka |
| D07 | Compra como convidado e conta opcional |
| D08 | Integrações de teste com Asaas Sandbox e Melhor Envio Sandbox |
| D09 | Túnel HTTPS temporário para receber webhooks |
| D10 | Pix e cartão por página hospedada pelo Asaas |
| D11 | Reserva de estoque por 15 minutos |
| D12 | Cancelamento antes da expedição e reembolso integral em sandbox |
| D13 | Pagamento confirmado após expiração da reserva entra em análise e inicia compensação por reembolso |
| D14 | Sem prazo fixo; avanço por entregas verificadas |
| D15 | Nome e identidade visual pendentes para a etapa de design, com sugestões de nomes agora |
| D16 | Planejar SDD, commits atômicos, verificação e validação; não escrever código nesta etapa |
| D17 | BRL, um único ponto de expedição/retirada e venda por unidade/SKU |
| D18 | Dividir a compra em vários pacotes quando não couber em um pacote |
| D19 | Deixar origem, retirada, preparação e validade pendentes para a revisão de logística; perguntar separadamente nessa etapa |
| D20 | Perguntar separadamente sobre cancelamento e cupons na etapa de logística, oferecendo recomendações |
| D21 | CEP de origem de teste: **66053-000**, Belém/PA (referência postal, sem vínculo comercial) |
| D22 | Retirada fictícia em “Ponto de demonstração — Belém” (sem atendimento presencial nem retirada real) |
| D23 | Retirada seg–sex, 9h–18h (horário de Belém), somente com pedido pronto |
| D24 | Preparação em até 1 dia útil após confirmação do pagamento, somada ao prazo de transporte |
| D25 | Preparação concluída até as 18h do próximo dia útil (horário de Belém) |
| D26 | Feriados nacionais, do Pará e de Belém suspendem preparação e retirada; calendário local configurável, sem API paga |
| D27 | Validade mínima restante na chegada prevista configurada por produto, usada na seleção de lotes |
| D28 | Tabela de alimentos fictícios e margens simuladas para revisão (valores aprovados em D54) |
| D29 | Cancelamento direto de entrega antes da entrega física à transportadora, mesmo com etiqueta emitida |
| D30 | Com pacote já entregue à transportadora: bloqueio do cancelamento direto; solicitação para análise, sem reembolso automático |
| D31 | Cancelamento de retirada até a confirmação pelo atendente; reembolso integral se pago; exclusão mútua com a conclusão da retirada |
| D32 | Experiência visual atraente e gravável para vídeo de portfólio, compreensível para público não técnico |
| D33 | Cupons limitados por e-mail verificado, limite configurável por cupom, sem exigir conta |
| D34 | Uso do cupom devolvido ao e-mail somente após reembolso integral confirmado |
| D35 | Alimentos separados de artesanato; cada peça frágil em pacote próprio |
| D36 | Tabela de caixas P/M/G para revisão (medidas aprovadas em D59; materiais em D60) |
| D37 | Item incompatível com entrega: retirada do pedido inteiro ou remoção explícita do item; sem combinar modalidades |
| D38 | Meta ≥80% cobertura de branches em checkout/pagamento/estoque/preço + cenários críticos (meta futura) |
| D39 | Meta local 50 compradores/5 min, p95 aceite <500 ms (meta futura) |
| D40 | Meta local 100 eventos em até 60 s após dependências disponíveis (meta futura) |
| D41 | Metas vitrine mobile LCP ≤2,5 s, CLS ≤0,1 em laboratório (metas futuras) |
| D42 | Repositório público desde o início da implementação |
| D43 | Direção visual editorial contemporânea |
| D44 | Finalistas: Entre Rios e De Lá do Pará |
| D45 | Nome: **De Lá do Pará** (sem confirmação de disponibilidade de marca/dominio) |
| D46 | Paleta: fundo marfim, texto verde profundo, detalhes terracota |
| D47 | Tipografia: títulos com serifa, textos/botões sem serifa |
| D48 | Tom de voz acolhedor e direto, com regionalismos pontuais |
| D49 | Vídeo principal vertical em loop ≤30 s, sem legendas (revisto pelo usuário em 20/09/2026; antes 60–90 s legendado) + demonstração técnica horizontal |
| D50 | Sequência: vitrine/origem → compra → falha breve → recuperação sem repetir a compra |
| D51 | Fotos gratuitas com licença verificada e autoria registrada; sem pessoas reais como produtores fictícios |
| D52 | Catálogo inicial: 8 produtos fictícios (4 alimentos + 4 artesanatos) |
| D53 | Composição: farinha, castanha, chocolate 70%, cacau, cuia, cesto, tigela, vaso |
| D54 | Preços/unidades/margens: farinha 500 g R$18/30d; castanha 200 g R$28/30d; chocolate 80 g R$22/45d; cacau 200 g R$24/60d; cuia R$45; cesto R$75; tigela R$65; vaso R$95 (fictícios) |
| D55 | Chocolate exclusivo para retirada na v1 |
| D56 | Cuia classificada como frágil; cesto não frágil; cerâmicas frágeis |
| D57 | Medidas/pesos simulados (C×L×A, peso bruto): farinha 20×14×5/520 g; castanha 16×12×4/220 g; chocolate 16×8×2/100 g; cacau 18×12×5/220 g; cuia 16×16×9/200 g; cesto 25×20×15/350 g; tigela 18×18×8/600 g; vaso 14×14×22/900 g |
| D58 | Proteção frágeis: 3 cm por lado (+6 cm/dimensão) e +100 g/peça |
| D59 | Caixas P 24×18×12 (ext. 25×19×13, 150 g, 2 kg); M 30×26×20 (31×27×21, 250 g, 5 kg); G 40×32×30 (41×33×31, 400 g, 10 kg) |
| D60 | Preenchimento papel: frágeis mantêm D58; não frágeis 1 cm/face + 50 g/pacote |
| D61 | Arquitetura: monólito modular Spring Boot; API e worker separados; PostgreSQL + Kafka; Angular separado |
| D62 | Monorepo: `backend/`, `frontend/`, `contracts/`, `infra/`, `docs/`, `specs/`, `tasks/` |
| D63 | Documentação da API e testes obrigatórios por funcionalidade |
| D64 | Auth: e-mail/senha, Spring Security + sessões PostgreSQL, cookie protegido, CSRF; compra convidada; e-mails no Mailpit |
| D65 | Produtor referenciado não pode ser excluído fisicamente; permite desativação. Exibir apenas localidade ampla e texto editorial fictício, ambos rotulados como demonstração, sem coordenadas/endereço nem alegações verificáveis |
| D66 | Pedido pronto para retirada fica guardado por 3 dias úteis. Após o prazo, abrir análise administrativa; sem cancelamento, descarte ou reembolso automáticos e mantendo o estoque comprometido até resolução explícita |
| D67 | Fixture de teste C25 usa lotes explicitamente sintéticos e relógio fixo: para cada alimento, um lote atende exatamente à margem D54 na chegada prevista e outro fica um dia abaixo. Não representa estoque real |
| D68 | Após qualquer pacote ser entregue à transportadora, os demais pacotes continuam o fluxo normal por padrão. Pausar pacotes ainda não despachados exige decisão administrativa; não há cancelamento, reembolso automático ou reembolso parcial |

## Propostas técnicas (revisão do plano, não respostas do usuário)

| ID | Proposta | Condição |
|---|---|---|
| A01 | Java 25, Spring Boot 4.1.x, Angular 22 | Fixar patches e matriz compatível no bootstrap (C06/C07) |
| A02 | Monólito modular, perfis API/worker, PostgreSQL, Kafka local | Confirmada em D61 |
| A03 | Outbox explícita + consumidores idempotentes; Modulith só para fronteiras | Validar compatibilidade em ADR (C05) |
| A04 | Sessões Spring Security/JDBC + cookie protegido + CSRF | Confirmada em D64 |
| A05 | SSR/hidratação em páginas públicas; rotas privadas sem cache público | ADR em C05 |
| A06 | Testcontainers para PostgreSQL/Kafka; simuladores para HTTP externo | Configurar em C09 |
| A07 | Perfis separados: local, sandbox, observabilidade | C08 |
| A08 | Uma moeda, um ponto, unidade/SKU | Confirmada em D17 |
| A09 | 80% branches no núcleo + metas locais | Aprovadas em D38–D41 como metas futuras |
| A10 | E-mail em Mailpit; conteúdo fictício identificado | Fluxos sem contratar serviço |

## Perguntas e encaminhamento

- **Q01 (aberta):** tons exatos, contraste, famílias de fontes e referências
  dentro da direção aprovada (D43–D48); storyboard/roteiro técnico e seleção
  de fotos (D51). **Momento:** C03 (design) — apresentar proposta concreta e
  revisar com o usuário. Não fechar automaticamente.
- **Resolvidas:** Q02→D17; Q07→D18; Q03a→D21; Q03b→D22; Q03c→D23; Q03d→D24;
  Q03e→D25; Q03f→D26; Q04a→D27; Q05a→D29; Q05b→D30; Q05c→D31; Q06a→D33;
  Q06b→D34; Q10a→D37; Q08a→D38; Q08b→D39; Q08c→D40; Q08d→D41; Q09→D42;
  Q04b→D54; Q10b→D55; Q10→D59/D60.
- **C24a (2026-09-21):** guarda de retirada de 3 dias úteis com análise manual
  (D66); fixture sintética de lotes com relógio fixo e fronteiras de validade
  (D67). São decisões do usuário; não representam estoque ou operação reais.
- **CAT-Q01 (C18, respondida pelo usuário em 2026-09-21):** produtores são
  geridos somente por administradores na v1; produtores não têm contas nem
  acesso próprio. A decisão não autoriza portal de produtor.
- **CAT-Q02/Q03 (C19, respondidas pelo usuário em 2026-09-21; D65):** produtor
  referenciado não pode ser apagado fisicamente; desativação é permitida. A
  procedência exposta limita-se a localidade ampla e texto editorial fictício,
  identificados como demonstração; não expor coordenadas, endereço ou alegação
  verificável.
- **Pendências reais por etapa** (sem inferência silenciosa): URL/owner/repo e
  namespace (C00a/C00b/C05 — repo criado, namespace `br.com.deladopara`
  proposto); specs e propostas A-abertas (C01/C02 + spec do módulo); tons/
  fontes/imagens/protótipo (C03); storyboard (C03, C96a/C96b); credenciais e
  dados de remetente no sandbox (C04); capacidade/idempotência dos provedores
  (C04/C53/C65); feriados/lotes/datas (specs inventory/shipping); guarda da
  retirada e validade tardia (specs orders/shipping); despacho parcial
  (specs checkout/payments/shipping); reserva global de cupons (spec pricing);
  licença/destino de releases/publicação (preparação repo/release).
