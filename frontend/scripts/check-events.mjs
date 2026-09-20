// Valida os exemplos do envelope de eventos contra o schema (C11).
// Uso: node scripts/check-events.mjs (a partir de frontend/).
import { readFileSync } from 'node:fs';
import { dirname, join, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';
import Ajv from 'ajv/dist/2020.js';
import addFormats from 'ajv-formats';

const root = resolve(dirname(fileURLToPath(import.meta.url)), '..', '..');
const schema = JSON.parse(
  readFileSync(join(root, 'contracts/events/envelope.schema.json'), 'utf8'),
);
const valid = JSON.parse(
  readFileSync(join(root, 'contracts/events/examples/order-created.valid.json'), 'utf8'),
);
const invalid = JSON.parse(
  readFileSync(join(root, 'contracts/events/examples/order-created.invalid.json'), 'utf8'),
);

const ajv = new Ajv({ allErrors: true, strict: true });
addFormats(ajv);
const validate = ajv.compile(schema);

let failed = false;
if (!validate(valid)) {
  console.error('ERRO: exemplo válido rejeitado:', validate.errors);
  failed = true;
} else {
  console.log('OK: order-created.valid.json aceito');
}
if (validate(invalid)) {
  console.error('ERRO: exemplo inválido aceito (deveria ser rejeitado)');
  failed = true;
} else {
  console.log('OK: order-created.invalid.json rejeitado como esperado');
}
process.exit(failed ? 1 : 0);
