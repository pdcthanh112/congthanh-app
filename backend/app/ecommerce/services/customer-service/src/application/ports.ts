import type { SearchEntry } from '../domain/search-history.js';
import type { ShippingAddressProps } from '../domain/shipping-address.js';
import type { WishlistItem } from '../domain/wishlist.js';

export interface AddressRepository {
  listByCustomer(customerId: string): Promise<ShippingAddressProps[]>;
  find(customerId: string, id: string): Promise<ShippingAddressProps | undefined>;
  findDefault(customerId: string): Promise<ShippingAddressProps | undefined>;
  count(customerId: string): Promise<number>;
  insert(address: ShippingAddressProps): Promise<void>;
  update(address: ShippingAddressProps): Promise<void>;
  delete(customerId: string, id: string): Promise<boolean>;
  /** Đặt `id` làm mặc định, bỏ mặc định các địa chỉ khác của khách (trong cùng transaction). */
  setDefault(customerId: string, id: string): Promise<void>;
  /** Khoá hàng của khách (SELECT ... FOR UPDATE) để hai request song song không phá quy tắc mặc định. */
  lockCustomer(customerId: string): Promise<void>;
}

export interface WishlistRepository {
  list(customerId: string, limit: number, offset: number): Promise<{ items: WishlistItem[]; total: number }>;
  has(customerId: string, productIds: readonly string[]): Promise<Set<string>>;
  count(customerId: string): Promise<number>;
  /** true nếu thêm mới (false nếu đã có). */
  add(customerId: string, productId: string, at: Date): Promise<boolean>;
  remove(customerId: string, productId: string): Promise<boolean>;
}

export interface SearchHistoryRepository {
  recent(userId: string, limit: number): Promise<SearchEntry[]>;
  /** Ghi (upsert theo từ khoá) và cắt bớt chỉ giữ `keep` bản mới nhất. */
  record(userId: string, keyword: string, at: Date, keep: number): Promise<void>;
  remove(userId: string, keyword: string): Promise<void>;
  clear(userId: string): Promise<void>;
}

export interface CustomerEvents {
  wishlistItemAdded(customerId: string, productId: string, at: Date): Promise<void>;
  wishlistItemRemoved(customerId: string, productId: string): Promise<void>;
}

export interface TransactionScope {
  addresses: AddressRepository;
  wishlist: WishlistRepository;
  searches: SearchHistoryRepository;
  events: CustomerEvents;
}

export interface UnitOfWork {
  run<T>(work: (scope: TransactionScope) => Promise<T>): Promise<T>;
}

export type ReadRepositories = Omit<TransactionScope, 'events'>;

export interface Clock {
  now(): Date;
}
export interface IdGenerator {
  next(): string;
}

export const UNIT_OF_WORK = Symbol('UNIT_OF_WORK');
export const READ_REPOSITORIES = Symbol('READ_REPOSITORIES');
export const CLOCK = Symbol('CLOCK');
export const ID_GENERATOR = Symbol('ID_GENERATOR');
