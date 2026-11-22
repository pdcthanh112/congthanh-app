import { customType } from "drizzle-orm/pg-core";
import { createHash } from "node:crypto";
import { hostname } from "node:os";

export const SNOWFLAKE_EPOCH_MS = 1609459200000n;
export const MAX_NODE = 1023;

export function nodeFromEnv(env: NodeJS.ProcessEnv = process.env): number {
  const raw = env.NODE_ID?.trim();
  if (raw) {
    const n = Number(raw);
    if (!Number.isInteger(n) || n < 0 || n > MAX_NODE) {
      throw new Error(`NODE_ID phải trong 0..${MAX_NODE}, nhận "${raw}"`);
    }
    return n;
  }
  const host = env.HOSTNAME || hostname() || String(process.pid);
  return createHash("sha256").update(host).digest().readUInt32BE(0) % (MAX_NODE + 1);
}

export class Snowflake {
  private last = -1n;
  private seq = 0n;
  private readonly node: bigint;

  constructor(
    node: number = nodeFromEnv(),
    private readonly clock: () => number = Date.now,
  ) {
    if (!Number.isInteger(node) || node < 0 || node > 1023) throw new Error(`node phải trong 0..1023, nhận ${node}`);
    this.node = BigInt(node);
  }

  next(): string {
    let now = BigInt(this.clock());
    if (now < this.last) now = this.last;
    if (now === this.last) {
      this.seq = (this.seq + 1n) & 0xfffn;
      if (this.seq === 0n) {
        while (now <= this.last) now = BigInt(this.clock());
      }
    } else {
      this.seq = 0n;
    }
    this.last = now;
    return (((now - SNOWFLAKE_EPOCH_MS) << 22n) | (this.node << 12n) | this.seq).toString();
  }
}

let shared: Snowflake | undefined;

export function nextSnowflakeId(): string {
  shared ??= new Snowflake();
  return shared.next();
}

const SNOWFLAKE_RE = /^\d{1,19}$/;

export function isSnowflakeId(v: string): boolean {
  return SNOWFLAKE_RE.test(v);
}

export const snowflakeId = customType<{ data: string; driverData: string }>({
  dataType: () => "bigint",
  toDriver: (v) => (SNOWFLAKE_RE.test(v) ? v : "-1"),
  fromDriver: (v) => String(v),
});
