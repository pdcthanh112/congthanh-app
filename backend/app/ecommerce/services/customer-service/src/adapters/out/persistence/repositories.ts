import type { Database } from '@ecommerce/platform';
import { and, count, desc, eq, inArray, lt, ne, sql } from 'drizzle-orm';
import type { AddressRepository, SearchHistoryRepository, WishlistRepository } from '../../../application/ports.js';
import type { ShippingAddressProps } from '../../../domain/shipping-address.js';
import type * as schema from './schema.js';
import { searchHistory, shippingAddresses, wishlistItems } from './schema.js';

export type Conn = Pick<Database<typeof schema>, 'select' | 'insert' | 'update' | 'delete' | 'execute'>;

export class DrizzleAddressRepository implements AddressRepository {
  constructor(private readonly db: Conn) {}

  listByCustomer(customerId: string): Promise<ShippingAddressProps[]> {
    return this.db
      .select()
      .from(shippingAddresses)
      .where(eq(shippingAddresses.customerId, customerId))
      .orderBy(desc(shippingAddresses.isDefault), desc(shippingAddresses.updatedAt));
  }

  async find(customerId: string, id: string) {
    const [row] = await this.db
      .select()
      .from(shippingAddresses)
      .where(and(eq(shippingAddresses.customerId, customerId), eq(shippingAddresses.id, id)));
    return row;
  }

  async findDefault(customerId: string) {
    const [row] = await this.db
      .select()
      .from(shippingAddresses)
      .where(and(eq(shippingAddresses.customerId, customerId), eq(shippingAddresses.isDefault, true)));
    return row;
  }

  async count(customerId: string) {
    const [row] = await this.db
      .select({ n: count() })
      .from(shippingAddresses)
      .where(eq(shippingAddresses.customerId, customerId));
    return row?.n ?? 0;
  }

  async insert(a: ShippingAddressProps) {
    await this.db.insert(shippingAddresses).values(a);
  }

  async update(a: ShippingAddressProps) {
    const { id, customerId, createdAt: _c, isDefault: _d, ...rest } = a;
    await this.db
      .update(shippingAddresses)
      .set(rest)
      .where(and(eq(shippingAddresses.customerId, customerId), eq(shippingAddresses.id, id)));
  }

  async delete(customerId: string, id: string) {
    const rows = await this.db
      .delete(shippingAddresses)
      .where(and(eq(shippingAddresses.customerId, customerId), eq(shippingAddresses.id, id)))
      .returning({ id: shippingAddresses.id });
    return rows.length > 0;
  }

  async setDefault(customerId: string, id: string) {
    // Bỏ mặc định cũ trước rồi mới đặt mới, để không vi phạm unique index một-mặc-định.
    await this.db
      .update(shippingAddresses)
      .set({ isDefault: false })
      .where(
        and(
          eq(shippingAddresses.customerId, customerId),
          eq(shippingAddresses.isDefault, true),
          ne(shippingAddresses.id, id),
        ),
      );
    await this.db
      .update(shippingAddresses)
      .set({ isDefault: true })
      .where(and(eq(shippingAddresses.customerId, customerId), eq(shippingAddresses.id, id)));
  }

  async lockCustomer(customerId: string) {
    // Khoá tư vấn theo khách trong transaction: tuần tự hoá thao tác địa chỉ của cùng một người.
    await this.db.execute(sql`select pg_advisory_xact_lock(hashtextextended(${`address:${customerId}`}, 0))`);
  }
}

export class DrizzleWishlistRepository implements WishlistRepository {
  constructor(private readonly db: Conn) {}

  async list(customerId: string, limit: number, offset: number) {
    const [items, [total]] = await Promise.all([
      this.db
        .select({ productId: wishlistItems.productId, addedAt: wishlistItems.addedAt })
        .from(wishlistItems)
        .where(eq(wishlistItems.customerId, customerId))
        .orderBy(desc(wishlistItems.addedAt))
        .limit(limit)
        .offset(offset),
      this.db.select({ n: count() }).from(wishlistItems).where(eq(wishlistItems.customerId, customerId)),
    ]);
    return { items, total: total?.n ?? 0 };
  }

  async has(customerId: string, productIds: readonly string[]) {
    if (productIds.length === 0) return new Set<string>();
    const rows = await this.db
      .select({ productId: wishlistItems.productId })
      .from(wishlistItems)
      .where(and(eq(wishlistItems.customerId, customerId), inArray(wishlistItems.productId, [...productIds])));
    return new Set(rows.map((r) => r.productId));
  }

  async count(customerId: string) {
    const [row] = await this.db
      .select({ n: count() })
      .from(wishlistItems)
      .where(eq(wishlistItems.customerId, customerId));
    return row?.n ?? 0;
  }

  async add(customerId: string, productId: string, at: Date) {
    const rows = await this.db
      .insert(wishlistItems)
      .values({ customerId, productId, addedAt: at })
      .onConflictDoNothing()
      .returning({ productId: wishlistItems.productId });
    return rows.length > 0;
  }

  async remove(customerId: string, productId: string) {
    const rows = await this.db
      .delete(wishlistItems)
      .where(and(eq(wishlistItems.customerId, customerId), eq(wishlistItems.productId, productId)))
      .returning({ productId: wishlistItems.productId });
    return rows.length > 0;
  }
}

export class DrizzleSearchHistoryRepository implements SearchHistoryRepository {
  constructor(private readonly db: Conn) {}

  recent(userId: string, limit: number) {
    return this.db
      .select({ keyword: searchHistory.keyword, searchedAt: searchHistory.searchedAt })
      .from(searchHistory)
      .where(eq(searchHistory.userId, userId))
      .orderBy(desc(searchHistory.searchedAt))
      .limit(limit);
  }

  async record(userId: string, keyword: string, at: Date, keep: number) {
    await this.db
      .insert(searchHistory)
      .values({ userId, keyword, searchedAt: at })
      .onConflictDoUpdate({ target: [searchHistory.userId, searchHistory.keyword], set: { searchedAt: at } });
    // Cắt bớt: xoá các từ khoá cũ hơn bản thứ `keep`.
    const [cutoff] = await this.db
      .select({ at: searchHistory.searchedAt })
      .from(searchHistory)
      .where(eq(searchHistory.userId, userId))
      .orderBy(desc(searchHistory.searchedAt))
      .offset(keep - 1)
      .limit(1);
    if (cutoff) {
      await this.db
        .delete(searchHistory)
        .where(and(eq(searchHistory.userId, userId), lt(searchHistory.searchedAt, cutoff.at)));
    }
  }

  async remove(userId: string, keyword: string) {
    await this.db
      .delete(searchHistory)
      .where(and(eq(searchHistory.userId, userId), eq(searchHistory.keyword, keyword)));
  }

  async clear(userId: string) {
    await this.db.delete(searchHistory).where(eq(searchHistory.userId, userId));
  }
}
