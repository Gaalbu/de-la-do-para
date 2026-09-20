# Brief de design — De Lá do Pará (proposta C03 para revisão)

Preserva D43–D48 e o nome D45. Tons, fontes, assets e protótipo são **proposta
concreta**: Q01 segue aberta e nada aqui fecha decisão sem revisão do usuário.
Nenhuma disponibilidade de marca, domínio ou rede é alegada.

## Direção

Editorial contemporânea (D43): fotos grandes, fundo claro, tipografia
marcante, cores paraenses nos detalhes; produto e história do produtor em
primeiro plano. Tom acolhedor e direto, regionalismos pontuais (D48);
compra, pagamento e erro sempre claros e objetivos.

## Paleta proposta (D46 + contraste medido localmente, WCAG)

| Uso | Cor | Hex | Contraste medido | Regra |
|---|---|---|---|---|
| Fundo | Marfim | `#FAF5EB` | — | fundo padrão |
| Texto | Verde profundo | `#1C3A2A` | 11,45:1 sobre marfim (AAA) | todo texto corrido e botões |
| Detalhe | Terracota | `#C05B2E` | 4,05:1 sobre marfim; 4,40:1 sobre branco | **só** títulos grandes (≥24 px ou ≥18,7 px bold), ícones gráficos e divisórias; nunca texto corrido |
| Cartão | Branco | `#FFFFFF` | 1,09:1 sobre marfim | usar com borda `#E7DCC8` ou sombra; texto interno sempre verde |
| Ação primária | Verde profundo sólido | `#1C3A2A` | texto marfim 11,45:1 (AAA) | botões comprar/continuar |

Limite honesto: terracota **não** passa em texto pequeno (exige 4,5:1);
o uso acima respeita 3:1 para grandes elementos e mantém leitura AAA no
restante. Foco visível de teclado usa contorno terracota 2 px + offset.

## Tipografia proposta (D47)

- **Títulos (serifa): Fraunces** — SIL OFL 1.1 verificada
  ([licença](https://github.com/google/fonts/raw/main/ofl/fraunces/OFL.txt),
  ©2018 The Fraunces Project Authors). Uso livre, inclusive embutir no app.
- **Textos/botões (sem serifa): Inter** — SIL OFL 1.1 verificada
  ([licença](https://github.com/google/fonts/raw/main/ofl/inter/OFL.txt),
  ©2020 The Inter Project Authors).
- Acentos pt-BR (ã, õ, ç, é…): ambos servem subset `latin-ext` no Google
  Fonts; confirmar no bootstrap (C07) com renderização de teste em celular.
- Escala mobile-first: título de vitrine 28–32 px, produto 24 px, corpo
  16 px mínimo, preço 20 px bold. Fallbacks: `Georgia, serif` / `system-ui`.

## Fluxos (protótipo a validar com usuário antes do frontend final)

1. **Catálogo:** grade com foto, nome, origem, preço e disponibilidade;
   filtros (produtor, preço, tipo) na URL; estados vazio/erro/carregando.
2. **Produto:** foto grande, história do produtor (fictício identificado),
   SKU, preço, conservação, dimensões/peso, disponibilidade; indisponível com
   estado claro, sem botão falso.
3. **Checkout:** endereço → entrega/retirada com pacotes, prazos e custo
   total → revisão → pagamento hospedado; chave de idempotência invisível,
   pendência/erro/sucesso como estados reais do backend.
4. **Acompanhamento:** linha do tempo pedido + pacotes; cancelamento e
   reembolso com progresso; retirada com ponto/prazo/código.
5. **Admin:** produtores, produtos, lotes/estoque, cupons, expedição e
   operações (falhas, quarentena, UNKNOWN) — mesma API autorizada, sem
   domínio duplicado.
6. **Mobile/teclado:** tudo operável por teclado, foco legível, sem bloqueio
   de ação por animação; respeito a `prefers-reduced-motion`; texto legível
   na gravação vista em celular.

## Direção anti-genérica (revisão do usuário, 20/09/2026)

Paleta, Fraunces + Inter e editorial fundo claro **aprovados**, com uma
condição: não parecer "visual vibecodado"/AIslop. Regras concretas:

- Fotos reais e específicas dos produtos mandam; nada de placeholders,
  gradientes roxo-azulados, blobs ou vidro fosco decorativo.
- Raio de borda e sombras contidos e consistentes; tipografia com hierarquia
  real (tamanhos/pesos com função), não títulos gigantes genéricos.
- Detalhes próprios da marca: terracota só onde o contraste permite,
  divisórias e etiquetas com função, microcopy em pt-BR com voz da loja.
- Todo componente novo passa pela pergunta: "isso poderia estar em qualquer
  template?" — se sim, redesenhar com conteúdo real da loja.

## Aberto para sua revisão (Q01) — respondido em 20/09/2026

1. Paleta aprovada + condição anti-genérica acima. ✅
2. Fraunces + Inter aprovadas; teste de acentos no bootstrap (C07). ✅
3. Fotos: editorial fundo claro. ✅ (curadoria em `assets.md`)
