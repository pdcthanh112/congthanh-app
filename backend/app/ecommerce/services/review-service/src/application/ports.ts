import type { Purchase } from '../domain/purchase.js';
import type { RatingSummary } from '../domain/rating-summary.js';
import type { Review, ReviewStatus } from '../domain/review.js';

export type ReviewSort = 'NEWEST' | 'HELPFUL' | 'RATING_HIGH' | 'RATING_LOW';

export interface ReviewQuery {
  productId?: string;
  customerId?: string;
  rating?: number;
  withMedia?: boolean;
  /** undefined = chỉ PUBLISHED (khách); backoffice truyền cụ thể. */
  status?: ReviewStatus | 'ALL';
  sort: ReviewSort;
  limit: number;
  offset: number;
}

export interface ReviewRepository {
  findById(id: string): Promise<Review | undefined>;
  exists(customerId: string, orderId: string, productId: string): Promise<boolean>;
  search(q: ReviewQuery): Promise<{ items: Review[]; total: number }>;
  insert(review: Review): Promise<void>;
  /** Khoá lạc quan: false nếu version đã đổi. */
  update(review: Review, expectedVersion: number): Promise<boolean>;
  delete(id: string): Promise<void>;
  /** true nếu là phiếu mới (mỗi người một phiếu "hữu ích"). */
  addHelpfulVote(reviewId: string, userId: string): Promise<boolean>;
}

export interface PurchaseRepository {
  upsertPlaced(orderId: string, customerId: string, productIds: readonly string[]): Promise<void>;
  setStatus(orderId: string, status: Purchase['status'], at: Date): Promise<void>;
  find(customerId: string, orderId: string, productId: string): Promise<Purchase | undefined>;
  /** Sản phẩm đã mua (CONFIRMED) mà khách chưa đánh giá. */
  pendingReviews(customerId: string, limit: number): Promise<Purchase[]>;
}

export interface SummaryRepository {
  get(productId: string): Promise<RatingSummary>;
  getMany(productIds: readonly string[]): Promise<Map<string, RatingSummary>>;
  /** Đọc có khoá (FOR UPDATE) trong transaction rồi ghi lại. */
  lockAndGet(productId: string): Promise<RatingSummary>;
  save(summary: RatingSummary): Promise<void>;
}

export interface ReviewEvents {
  submitted(review: Review): Promise<void>;
  moderated(review: Review, moderatorId: string): Promise<void>;
  replied(review: Review, sellerId: string): Promise<void>;
  ratingChanged(summary: RatingSummary): Promise<void>;
}

export interface TransactionScope {
  reviews: ReviewRepository;
  purchases: PurchaseRepository;
  summaries: SummaryRepository;
  events: ReviewEvents;
  /** Idempotent consumer: false nếu event này đã xử lý. */
  markProcessed(eventId: string, consumer: string): Promise<boolean>;
}

export interface UnitOfWork {
  run<T>(work: (scope: TransactionScope) => Promise<T>): Promise<T>;
}

export type ReadRepositories = Pick<TransactionScope, 'reviews' | 'purchases' | 'summaries'>;

export interface ProductDirectory {
  sellerOf(productId: string): Promise<string | undefined>;
}

export interface Clock {
  now(): Date;
}
export interface IdGenerator {
  next(): string;
}
export interface ReviewSettings {
  /** URL gốc của file do file-service cấp (CDN), media khác bị từ chối. */
  mediaUrlPrefix: string;
}

export const UNIT_OF_WORK = Symbol('UNIT_OF_WORK');
export const READ_REPOSITORIES = Symbol('READ_REPOSITORIES');
export const PRODUCT_DIRECTORY = Symbol('PRODUCT_DIRECTORY');
export const CLOCK = Symbol('CLOCK');
export const ID_GENERATOR = Symbol('ID_GENERATOR');
export const REVIEW_SETTINGS = Symbol('REVIEW_SETTINGS');
