// Valida todos os exemplos *.valid.json / *.invalid.json contra o envelope (C11, C53).
// Uso: node scripts/check-events.mjs (a partir de frontend/).
import { readdirSync, readFileSync } from 'node:fs';
import { dirname, join, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';
import Ajv from 'ajv/dist/2020.js';
import addFormats from 'ajv-formats';

const root = resolve(dirname(fileURLToPath(import.meta.url)), '..', '..');
const schema = JSON.parse(
  readFileSync(join(root, 'contracts/events/envelope.schema.json'), 'utf8'),
);
const examples = join(root, 'contracts/events/examples');
const ajv = new Ajv({ allErrors: true, strict: true });
addFormats(ajv);
const validate = ajv.compile(schema);

let failed = false;
for (const file of readdirSync(examples).sort()) {
  const expectValid = file.endsWith('.valid.json');
  if (!expectValid && !file.endsWith('.invalid.json')) continue;
  const accepted = validate(JSON.parse(readFileSync(join(examples, file), 'utf8')));
  if (accepted === expectValid) {
    console.log(`OK: ${file} ${expectValid ? 'aceito' : 'rejeitado como esperado'}`);
  } else {
    console.error(`ERRO: ${file} ${expectValid ? 'rejeitado' : 'aceito indevidamente'}`, validate.errors ?? '');
    failed = true;
  }
}
process.exit(failed ? 1 : 0);
