import { defineConfig } from '@hey-api/openapi-ts';

// Cliente Angular gerado do contrato canônico (C11). Saída em
// src/generated (ignorado no Git): contracts:generate sempre regenera do
// zero, então o gerado nunca diverge nem é editado manualmente.
export default defineConfig({
  input: '../contracts/openapi/v1.yaml',
  output: 'src/generated',
});
