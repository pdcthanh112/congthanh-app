import { DomainError } from '@ecom/platform/errors';
import { Inject, Injectable } from '@nestjs/common';
import { normalizeKeyword, SEARCH_HISTORY_LIMIT, type SearchEntry } from '../domain/search-history.js';
import {
  type AddressDraft,
  MAX_ADDRESSES_PER_CUSTOMER,
  nextDefaultAfterDelete,
  normalizeAddress,
  type ShippingAddressProps,
} from '../domain/shipping-address.js';
import { MAX_WISHLIST_ITEMS, type WishlistItem } from '../domain/wishlist.js';
import {
  CLOCK,
  type Clock,
  ID_GENERATOR,
  type IdGenerator,
  READ_REPOSITORIES,
  type ReadRepositories,
  UNIT_OF_WORK,
  type UnitOfWork,
} from './ports.js';

/** Use case của khách hàng. Mọi thao tác đều theo `customerId` = `sub` của token (không nhận từ client). */
@Injectable()
export class CustomerService {
  constructor(
    @Inject(UNIT_OF_WORK) private readonly uow: UnitOfWork,
    @Inject(READ_REPOSITORIES) readonly read: ReadRepositories,
    @Inject(CLOCK) private readonly clock: Clock,
    @Inject(ID_GENERATOR) private readonly ids: IdGenerator,
  ) {}

  // ---------- địa chỉ giao hàng ----------

  addAddress(customerId: string, draft: AddressDraft, makeDefault: boolean): Promise<ShippingAddressProps> {
    const data = normalizeAddress(draft);
    return this.uow.run(async (tx) => {
      await tx.addresses.lockCustomer(customerId);
      const count = await tx.addresses.count(customerId);
      if (count >= MAX_ADDRESSES_PER_CUSTOMER) {
        throw DomainError.invalid(`at most ${MAX_ADDRESSES_PER_CUSTOMER} addresses`, 'ADDRESS_LIMIT');
      }
      const now = this.clock.now();
      const address: ShippingAddressProps = {
        ...data,
        id: this.ids.next(),
        customerId,
        isDefault: false,
        createdAt: now,
        updatedAt: now,
      };
      await tx.addresses.insert(address);
      if (makeDefault || count === 0) {
        await tx.addresses.setDefault(customerId, address.id);
        address.isDefault = true;
      }
      return address;
    });
  }

  updateAddress(customerId: string, id: string, draft: AddressDraft): Promise<ShippingAddressProps> {
    const data = normalizeAddress(draft);
    return this.uow.run(async (tx) => {
      const current = await tx.addresses.find(customerId, id);
      if (!current) throw DomainError.notFound(`address ${id} not found`);
      const next = { ...current, ...data, updatedAt: this.clock.now() };
      await tx.addresses.update(next);
      return next;
    });
  }

  setDefaultAddress(customerId: string, id: string): Promise<ShippingAddressProps> {
    return this.uow.run(async (tx) => {
      await tx.addresses.lockCustomer(customerId);
      const current = await tx.addresses.find(customerId, id);
      if (!current) throw DomainError.notFound(`address ${id} not found`);
      await tx.addresses.setDefault(customerId, id);
      return { ...current, isDefault: true };
    });
  }

  deleteAddress(customerId: string, id: string): Promise<boolean> {
    return this.uow.run(async (tx) => {
      await tx.addresses.lockCustomer(customerId);
      const current = await tx.addresses.find(customerId, id);
      if (!current) return false;
      await tx.addresses.delete(customerId, id);
      if (current.isDefault) {
        const next = nextDefaultAfterDelete(await tx.addresses.listByCustomer(customerId));
        if (next) await tx.addresses.setDefault(customerId, next);
      }
      return true;
    });
  }

  // ---------- wishlist ----------

  wishlist(customerId: string, page: number, size: number): Promise<{ items: WishlistItem[]; total: number }> {
    const limit = Math.min(Math.max(size, 1), 100);
    return this.read.wishlist.list(customerId, limit, Math.max(page - 1, 0) * limit);
  }

  addToWishlist(customerId: string, productId: string): Promise<boolean> {
    if (!productId?.trim()) throw DomainError.invalid('productId is required');
    return this.uow.run(async (tx) => {
      if ((await tx.wishlist.count(customerId)) >= MAX_WISHLIST_ITEMS) {
        throw DomainError.invalid(`wishlist is limited to ${MAX_WISHLIST_ITEMS} items`, 'WISHLIST_FULL');
      }
      const now = this.clock.now();
      const added = await tx.wishlist.add(customerId, productId, now);
      if (added) await tx.events.wishlistItemAdded(customerId, productId, now);
      return true;
    });
  }

  removeFromWishlist(customerId: string, productId: string): Promise<boolean> {
    return this.uow.run(async (tx) => {
      const removed = await tx.wishlist.remove(customerId, productId);
      if (removed) await tx.events.wishlistItemRemoved(customerId, productId);
      return removed;
    });
  }

  // ---------- lịch sử tìm kiếm ----------

  recentSearches(userId: string, limit: number): Promise<SearchEntry[]> {
    return this.read.searches.recent(userId, Math.min(Math.max(limit, 1), SEARCH_HISTORY_LIMIT));
  }

  recordSearch(userId: string, keyword: string): Promise<void> {
    return this.read.searches.record(userId, normalizeKeyword(keyword), this.clock.now(), SEARCH_HISTORY_LIMIT);
  }

  removeSearch(userId: string, keyword: string): Promise<void> {
    return this.read.searches.remove(userId, normalizeKeyword(keyword));
  }

  clearSearches(userId: string): Promise<void> {
    return this.read.searches.clear(userId);
  }
}
