# SPEC — Endereço e escolha de entrega no checkout

Status: contrato executável para C44; não autoriza cobrança, reserva de estoque
ou criação de pedido.

## Escopo

O checkout lê um snapshot imutável do carrinho e coleta somente os dados
necessários para obter opções de entrega. O cliente nunca calcula frete, prazo
ou total: esses valores vêm do servidor e permanecem ligados ao snapshot.

## Endereço

Para entrega, o cliente envia `postalCode` como oito dígitos, opcionalmente
formatado com hífen, e os campos complementares exigidos pelo serviço de
destino. O servidor normaliza o CEP antes de cotar e rejeita CEP inválido,
ausente ou incompatível com a modalidade. A interface não preenche rua,
número, bairro ou complemento por inferência.

Retirada não exige endereço de entrega e usa somente um ponto de retirada
retornado pelo servidor. O ponto sintético de demonstração deve ser claramente
identificado e não pode ser apresentado como atendimento comercial.

## Cotação

`GET /api/v1/checkout/{snapshotId}/delivery-options` recebe a versão do
snapshot e o endereço normalizado. A resposta contém `snapshotVersion`, um
`inputFingerprint`, estado (`AVAILABLE` ou `UNAVAILABLE`) e, quando disponível,
as opções:

```json
{
  "snapshotVersion": 3,
  "inputFingerprint": "sha256:...",
  "options": [
    {
      "id": "quote-uuid",
      "mode": "DELIVERY",
      "label": "Sandbox PAC",
      "priceCents": 2590,
      "preparationDays": 2,
      "deliveryDays": 5,
      "expiresAt": "2026-09-22T13:00:00Z"
    }
  ]
}
```

`UNAVAILABLE` é uma resposta recuperável: a UI mantém o carrinho e oferece
alterar o endereço, escolher retirada quando compatível ou tentar nova cotação.
Não exibe custo ou prazo estimado sem uma opção persistida e válida.

## Invalidação

Alterar qualquer item do carrinho, sua versão, o endereço ou a modalidade
descarta a seleção local e exige nova cotação. Aceitar uma opção envia seu
`quoteId`, `snapshotVersion` e `inputFingerprint`; o servidor revalida os três,
a disponibilidade e `expiresAt`, retornando `409` para identidade divergente e
`410` para cotação expirada.

## Retirada

Uma opção de retirada contém ponto, janela e prazo de preparo retornados pelo
servidor. A UI não cria horários, não remove itens incompatíveis silenciosamente
e mantém a escolha separada das opções de entrega.

## Erros

- `400`: endereço ou modalidade inválida;
- `404`: snapshot inexistente ou não pertencente ao visitante;
- `409`: snapshot, fingerprint ou seleção obsoleta;
- `410`: cotação expirada;
- `422`: composição sem modalidade compatível.

Todos os problemas usam `codigo` e `correlationId`; não expõem credenciais de
adapter nem o identificador bruto da sessão.
