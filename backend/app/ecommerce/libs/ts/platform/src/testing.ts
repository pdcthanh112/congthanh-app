import type { Principal } from './auth/principal.js';

/** Principal giả cho test. */
export function testPrincipal(id = 'user-1', roles: string[] = ['CUSTOMER']): Principal {
  return { id, roles: new Set(roles), token: 'test-token' };
}
