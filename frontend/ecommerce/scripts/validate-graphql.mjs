#!/usr/bin/env node
// Kiểm mọi document trong src/graphql/documents.ts với supergraph storefront (đường dẫn mặc định trong monorepo).
//   node scripts/validate-graphql.mjs [path/to/supergraph.graphql]   (npm run graphql:check)
import { readFileSync } from 'node:fs';
import { createRequire } from 'node:module';

// Lấy package graphql từ thư mục đang chạy (frontend hoặc backend/app/ecommerce trong CI).
const { buildSchema, parse, validate } = createRequire(`${process.cwd()}/package.json`)('graphql');

const sdlPath = process.argv[2] ?? '../../backend/app/ecommerce/platform/graphql/storefront/supergraph.graphql';
const schema = buildSchema(readFileSync(sdlPath, 'utf8'), { assumeValidSDL: true });
const src = readFileSync(new URL('../src/graphql/documents.ts', import.meta.url), 'utf8');

// Thay các hằng fragment (const X = '...' hoặc `...`) vào chỗ ${X} như lúc chạy.
const consts = {};
for (const [, name, body] of src.matchAll(/^const ([A-Z_]+) = ['`]([^'`]*)['`];/gm)) {
  consts[name] = body.replace(/\$\{([A-Z_]+)\}/g, (_, n) => consts[n]);
}
const docs = [...src.matchAll(/export const (\w+) = doc<[\s\S]*?>\(\s*`([\s\S]*?)`\s*\);/g)];
let bad = 0;
for (const [, name, body] of docs) {
  const text = body.replace(/\$\{([A-Z_]+)\}/g, (_, n) => consts[n] ?? `__missing_${n}`);
  let errs;
  try {
    errs = validate(schema, parse(text)).map((e) => e.message);
  } catch (e) {
    errs = [String(e.message)];
  }
  if (errs.length) {
    bad++;
    console.error(`✗ ${name}: ${errs.join('; ')}`);
  }
}
console.log(`${docs.length - bad}/${docs.length} operations valid`);
process.exit(bad || docs.length === 0 ? 1 : 0);
