/** Người gọi đã xác thực (từ access token Keycloak). */
export interface Principal {
  /** `sub` của token: id người dùng trong Keycloak, dùng làm customerId/userId ở mọi service. */
  readonly id: string;
  readonly username?: string;
  readonly email?: string;
  readonly name?: string;
  /** Realm role: CUSTOMER, SELLER, ADMIN, SUPPORT. */
  readonly roles: ReadonlySet<string>;
  /** Access token gốc, để chuyển tiếp xuống service phía sau (token relay). */
  readonly token: string;
}

export const Role = {
  CUSTOMER: 'CUSTOMER',
  SELLER: 'SELLER',
  ADMIN: 'ADMIN',
  SUPPORT: 'SUPPORT',
} as const;
export type Role = (typeof Role)[keyof typeof Role];

export function hasAnyRole(p: Principal | undefined, roles: readonly string[]): boolean {
  return !!p && roles.some((r) => p.roles.has(r));
}
