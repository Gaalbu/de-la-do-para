# Storyboard — vídeos de portfólio (revisão C03 aprovada em 20/09/2026)

Principal: vídeo **vertical em loop de no máximo ~30 s, sem legendas** —
revisão do usuário em 20/09/2026 alterando D49 (antes 60–90 s legendado):
apenas interface e fluxo esperado em loop, com um texto único para leitura
enquanto o vídeo toca. Demonstração técnica **horizontal** separada mantida.
Sequência do principal segue D50 (vitrine/origem → compra → falha breve →
recuperação sem repetir a compra). Telas mostram **estados reais**; a falha
é induzida em ambiente isolado, sem controles técnicos na jornada do
cliente. Dados fictícios, sem PII (D51).

## Principal vertical (loop ≤30 s, sem legendas)

Uma tomada contínua em loop: vitrine → produto/origem → carrinho →
entrega/retirada → pagamento (sandbox identificado) → pedido confirmado,
com a falha/recuperação visível como estado real sem repetir a compra.
Texto único de acompanhamento (para leitura durante o loop, fora do vídeo
ou na descrição do post — não queimado como legenda cena a cena).

Critério de aceite do vídeo: pessoa não técnica explica o que a loja vende,
de onde vêm os produtos e o que ocorreu com o pedido.

## Demonstração técnica horizontal (roteiro)

1. Compra com Kafka saudável (pedido, reserva, outbox, confirmação).
2. Kafka interrompido → compra pendente durável → Kafka de volta → publica
   sem duplicar efeito (V04/V05).
3. Webhook repetido/fora de ordem sem regressão (V08); timeout com UNKNOWN e
   conciliação, sem recriar cobrança (V07).
4. Expiração de reserva + pagamento tardio em compensação (V10).
5. Trace HTTP → outbox → Kafka → efeito; replay auditado da quarentena.

## Revisão — respondida em 20/09/2026

1. Principal revisto: loop ≤30 s, sem legendas, texto único (ver topo). ✅
2. Roteiro técnico aprovado como está (V04/V05, V07/V08, V10 + traces). ✅
