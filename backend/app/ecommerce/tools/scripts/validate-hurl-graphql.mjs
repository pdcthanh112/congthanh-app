#!/usr/bin/env node
// Kiểm tra truy vấn GraphQL trong file Hurl khớp supergraph đã compose (CI: sai tên trường thì hỏng sớm).
//   node tools/scripts/validate-hurl-graphql.mjs tests/e2e/graphql.hurl
import { readFileSync } from 'node:fs';
import { createRequire } from 'node:module';
const require = createRequire(process.cwd() + '/package.json');
const { buildSchema, parse, validate } = require('graphql');
const hurl = readFileSync(process.argv[2], 'utf8');
const blocks = [...hurl.matchAll(/POST \{\{base\}\}\/(admin\/)?graphql[\s\S]*?```graphql\n([\s\S]*?)```/g)];
let bad = 0;
for (const [, admin, q] of blocks) {
  const sdl = readFileSync(`platform/graphql/${admin ? 'backoffice' : 'storefront'}/supergraph.graphql`, 'utf8');
  const errs = validate(buildSchema(sdl, { assumeValidSDL: true }), parse(q));
  console.log(admin ? 'backoffice' : 'storefront', errs.length ? errs.map((e) => e.message).join('; ') : 'ok');
  bad += errs.length;
}
process.exit(bad ? 1 : 0);
