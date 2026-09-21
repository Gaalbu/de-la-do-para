// C13a: valida links internos de Markdown em docs/ e README (sem rede).
// Falha se um link relativo apontar para arquivo inexistente.
import { readdirSync, readFileSync, statSync } from 'node:fs';
import { join, dirname, resolve, normalize } from 'node:path';
import { fileURLToPath } from 'node:url';

const root = resolve(dirname(fileURLToPath(import.meta.url)), '..', '..');
const docsRoot = join(root, 'docs');
const tracked = [join(root, 'README.md')];

function collectMarkdown(dir, out) {
  for (const entry of readdirSync(dir, { withFileTypes: true })) {
    const p = join(dir, entry.name);
    if (entry.isDirectory()) collectMarkdown(p, out);
    else if (entry.isFile() && entry.name.endsWith('.md')) out.push(p);
  }
}

const files = [];
collectMarkdown(docsRoot, files);
files.push(...tracked);

const linkRe = /\[([^\]]*)\]\(([^)]+)\)/g;
let broken = [];

for (const file of files) {
  let text;
  try {
    text = readFileSync(file, 'utf8');
  } catch {
    continue;
  }
  let m;
  while ((m = linkRe.exec(text))) {
    let target = m[2].trim();
    // ignora comandos, âncoras puras e URLs externas
    if (!target) continue;
    // remove título após espaço: "url \"title\""
    const spaceIdx = target.search(/\s+"/);
    if (spaceIdx !== -1) target = target.slice(0, spaceIdx);
    target = target.replace(/^<|>$/g, '').trim();
    if (
      target.startsWith('http://') ||
      target.startsWith('https://') ||
      target.startsWith('mailto:') ||
      target.startsWith('#') ||
      target.startsWith('data:')
    )
      continue;
    // remove âncora e query
    const hash = target.indexOf('#');
    if (hash !== -1) target = target.slice(0, hash);
    const q = target.indexOf('?');
    if (q !== -1) target = target.slice(0, q);
    if (!target) continue;
    // caminho absoluto a partir da raiz quando começa com /
    let candidate;
    if (target.startsWith('/')) {
      candidate = join(root, target.slice(1));
    } else {
      candidate = resolve(dirname(file), target);
      // fallback: se não existe e é caminho sem ./ ou ../, tenta da raiz
      try {
        statSync(candidate);
      } catch {
        if (!target.startsWith('./') && !target.startsWith('../')) {
          const alt = join(root, target);
          try {
            statSync(alt);
            candidate = alt;
          } catch {
            // mantém candidate original para reportar
          }
        }
      }
    }
    try {
      statSync(candidate);
    } catch {
      broken.push(
        `${file.replace(root + '/', '')}: [${m[1]}](${m[2]}) -> ${candidate.replace(root + '/', '')} não encontrado`,
      );
    }
  }
}

if (broken.length) {
  console.error(`ERRO: ${broken.length} link(s) quebrado(s) em docs/README:`);
  for (const b of broken) console.error(' -', b);
  process.exit(1);
}
console.log(`OK: ${files.length} arquivo(s) Markdown verificados, nenhum link quebrado`);
