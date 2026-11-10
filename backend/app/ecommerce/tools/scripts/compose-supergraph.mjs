#!/usr/bin/env node
/**
 * Compose các supergraph GraphQL Federation bằng wgc (WunderGraph Cosmo), không cần control plane.
 *
 *   npm run supergraph          → ghi platform/graphql/<graph>/config.json (router đọc, không commit)
 *                                 và supergraph.graphql (schema client thấy, có commit để review diff)
 *   npm run supergraph:check    → compose vào thư mục tạm; lỗi nếu composition hỏng hoặc
 *                                 supergraph.graphql đã commit lệch với schema hiện tại.
 *
 * Trước đó chạy `npm run build && npm run schema:emit --workspaces --if-present` để mỗi service ghi
 * lại schema.graphql (SDL subgraph).
 */
import { execFileSync } from 'node:child_process';
import { existsSync, mkdtempSync, readdirSync, readFileSync, rmSync, writeFileSync } from 'node:fs';
import { tmpdir } from 'node:os';
import { dirname, join, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';

const root = resolve(dirname(fileURLToPath(import.meta.url)), '..', '..');
const graphsDir = join(root, 'platform', 'graphql');
const check = process.argv.includes('--check');
const wgc = join(root, 'node_modules', '.bin', process.platform === 'win32' ? 'wgc.cmd' : 'wgc');

const graphs = readdirSync(graphsDir, { withFileTypes: true })
  .filter((d) => d.isDirectory() && existsSync(join(graphsDir, d.name, 'graph.yaml')))
  .map((d) => d.name);

const tmp = check ? mkdtempSync(join(tmpdir(), 'supergraph-')) : null;
let failed = false;

for (const graph of graphs) {
  const dir = join(graphsDir, graph);
  const out = tmp ? join(tmp, `${graph}.json`) : join(dir, 'config.json');
  try {
    // wgc đọc đường dẫn schema tương đối theo thư mục hiện tại nên chạy trong thư mục của graph.
    execFileSync(wgc, ['router', 'compose', '-i', 'graph.yaml', '-o', out], {
      cwd: dir,
      stdio: ['ignore', 'pipe', 'pipe'],
      env: { ...process.env, NO_UPDATE_NOTIFIER: '1' },
    });
  } catch (e) {
    failed = true;
    console.error(`✗ ${graph}: composition failed\n${e.stdout ?? ''}${e.stderr ?? ''}`);
    continue;
  }
  const sdl = `${JSON.parse(readFileSync(out, 'utf8')).engineConfig.graphqlSchema.trim()}\n`;
  const sdlFile = join(dir, 'supergraph.graphql');
  const header = `# Sinh bởi tools/scripts/compose-supergraph.mjs từ graph.yaml — không sửa tay.\n`;
  if (check) {
    const committed = existsSync(sdlFile) ? readFileSync(sdlFile, 'utf8') : '';
    if (committed !== header + sdl) {
      failed = true;
      console.error(`✗ ${graph}: supergraph.graphql is stale; run "npm run supergraph" and commit the result`);
      continue;
    }
  } else {
    writeFileSync(sdlFile, header + sdl);
  }
  console.log(`✓ ${graph}`);
}

if (tmp) rmSync(tmp, { recursive: true, force: true });
process.exit(failed ? 1 : 0);
