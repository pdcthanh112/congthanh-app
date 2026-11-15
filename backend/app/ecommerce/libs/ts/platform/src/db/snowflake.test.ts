import { describe, expect, it } from 'vitest';
import { nextSnowflakeId, nodeFromEnv, SNOWFLAKE_EPOCH_MS, Snowflake } from './snowflake.js';

describe('Snowflake', () => {
  it('encodes time, node and sequence', () => {
    const t = Date.parse('2026-11-15T00:00:00Z');
    const g = new Snowflake(37, () => t);
    const a = BigInt(g.next());
    const b = BigInt(g.next());
    expect(a >> 22n).toBe(BigInt(t) - SNOWFLAKE_EPOCH_MS);
    expect((a >> 12n) & 0x3ffn).toBe(37n);
    expect(b).toBe(a + 1n);
  });

  it('is unique and increasing', () => {
    let prev = -1n;
    const seen = new Set<string>();
    for (let i = 0; i < 20_000; i++) {
      const id = nextSnowflakeId();
      expect(BigInt(id) > prev).toBe(true);
      seen.add(id);
      prev = BigInt(id);
    }
    expect(seen.size).toBe(20_000);
  });

  it('reads NODE_ID or hashes the host name', () => {
    expect(nodeFromEnv({ NODE_ID: '5' })).toBe(5);
    expect(() => nodeFromEnv({ NODE_ID: '1024' })).toThrow();
    expect(nodeFromEnv({ HOSTNAME: 'pod-a' })).toBe(nodeFromEnv({ HOSTNAME: 'pod-a' }));
  });
});
