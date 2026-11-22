export interface Principal {
  readonly id: string;
  readonly username?: string;
  readonly email?: string;
  readonly name?: string;
  readonly roles: ReadonlySet<string>;
  readonly token: string;
}

export const Role = {
  CUSTOMER: "CUSTOMER",
  SELLER: "SELLER",
  ADMIN: "ADMIN",
  SUPPORT: "SUPPORT",
} as const;
export type Role = (typeof Role)[keyof typeof Role];

export function hasAnyRole(p: Principal | undefined, roles: readonly string[]): boolean {
  return !!p && roles.some((r) => p.roles.has(r));
}
