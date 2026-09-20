# Storyboard — vídeos de portfólio (proposta C03 para revisão)

Formatos aprovados em D49: principal **vertical 60–90 s**, legendado e
legível no celular; demonstração técnica **horizontal** separada. Sequência
do principal aprovada em D50. Tempos/cortes/legendas abaixo são proposta.
Telas mostram **estados reais**; a falha é induzida em ambiente isolado, sem
controles técnicos na jornada do cliente. Dados fictícios, sem PII (D51).

## Principal vertical (alvo 75 s)

| Tempo | Cena | Legenda curta |
|---|---|---|
| 0–10 s | Vitrine: fotos grandes, nome e tipografia | “Produtos do Pará, com origem e produtor” |
| 10–25 s | Página de produto + história do produtor fictício (identificado como demonstração) | “Cada produto conta de onde vem” |
| 25–45 s | Carrinho → entrega/retirada → revisão → pagamento (sandbox identificado) | “Compra como convidado, sem conta” |
| 45–70 s | Falha breve controlada → pedido pendente → recuperação → confirmado, **sem repetir a compra** | “Falhou, recuperou — sem duplicar” |
| 70–90 s | Pedido confirmado + resumo autoria/tecnologias | “Spring Boot · Angular · Kafka” |

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

## Aberto para sua revisão

1. Duração-alvo dentro de 60–90 s e legendas acima aprovadas?
2. Roteiro técnico cobre o diferencial ou falta algum cenário V01–V22?
