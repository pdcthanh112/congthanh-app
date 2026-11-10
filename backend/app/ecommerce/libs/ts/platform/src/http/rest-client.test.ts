import { createServer, type Server } from 'node:http';
import type { AddressInfo } from 'node:net';
import { afterAll, beforeAll, describe, expect, it } from 'vitest';
import { z } from 'zod';
import { RestClient } from './rest-client.js';

describe('RestClient', () => {
  let server: Server;
  let base: string;
  const hits = new Map<string, number>();

  beforeAll(async () => {
    server = createServer((req, res) => {
      const path = new URL(req.url ?? '/', 'http://x').pathname;
      hits.set(`${req.method} ${path}`, (hits.get(`${req.method} ${path}`) ?? 0) + 1);
      res.setHeader('content-type', 'application/json');
      if (path === '/ok') return res.end(JSON.stringify({ data: { id: 1, auth: req.headers.authorization ?? null } }));
      if (path === '/missing') {
        res.statusCode = 404;
        return res.end(JSON.stringify({ title: 'Not Found', detail: 'product x not found' }));
      }
      if (path === '/busy') {
        res.statusCode = 503;
        return res.end('{}');
      }
      if (path === '/bad-shape') return res.end(JSON.stringify({ data: { id: 'not-a-number' } }));
      res.statusCode = 500;
      res.end('{}');
    }).listen(0);
    await new Promise((r) => server.once('listening', r));
    base = `http://127.0.0.1:${(server.address() as AddressInfo).port}`;
  });
  afterAll(() => server.close());

  const client = () => new RestClient({ baseUrl: base, name: 'svc', retries: 2, timeoutMs: 2_000 });

  it('bóc envelope {data}, chuyển tiếp token', async () => {
    const r = await client().data('/ok', z.object({ id: z.number(), auth: z.string().nullable() }), { token: 't1' });
    expect(r).toEqual({ id: 1, auth: 'Bearer t1' });
  });

  it('404 → NOT_FOUND kèm chi tiết từ Problem Details', async () => {
    await expect(client().call('/missing', z.unknown())).rejects.toMatchObject({ kind: 'NOT_FOUND' });
  });

  it('503: GET được thử lại rồi báo UNAVAILABLE; POST không thử lại', async () => {
    await expect(client().call('/busy', z.unknown())).rejects.toMatchObject({ kind: 'UNAVAILABLE' });
    expect(hits.get('GET /busy')).toBe(3);
    await expect(client().call('/busy', z.unknown(), { method: 'POST', body: {} })).rejects.toMatchObject({
      kind: 'UNAVAILABLE',
    });
    expect(hits.get('POST /busy')).toBe(1);
  });

  it('không kết nối được → UNAVAILABLE', async () => {
    const c = new RestClient({ baseUrl: 'http://127.0.0.1:1', name: 'down', retries: 0 });
    await expect(c.call('/x', z.unknown())).rejects.toMatchObject({ kind: 'UNAVAILABLE' });
  });

  it('sai hợp đồng dữ liệu → UNAVAILABLE với thông báo rõ', async () => {
    await expect(client().data('/bad-shape', z.object({ id: z.number() }))).rejects.toThrow(/unexpected payload/);
  });
});
