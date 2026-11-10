import type { Database } from '@ecom/platform';
import { and, asc, count, desc, eq, inArray, isNull, type SQL, sql } from 'drizzle-orm';
import type {
  PurchaseRepository,
  ReviewQuery,
  ReviewRepository,
  SummaryRepository,
} from '../../../application/ports.js';
import type { Purchase } from '../../../domain/purchase.js';
import { emptySummary, type RatingSummary } from '../../../domain/rating-summary.js';
import { Review } from '../../../domain/review.js';
import type * as schema from './schema.js';
import { helpfulVotes, purchases, ratingSummaries, reviews } from './schema.js';

export type Conn = Pick<Database<typeof schema>, 'select' | 'insert' | 'update' | 'delete' | 'execute'>;

const toReview = (r: typeof reviews.$inferSelect) => Review.rehydrate(r);

export class DrizzleReviewRepository implements ReviewRepository {
  constructor(private readonly db: Conn) {}

  async findById(id: string) {
    const [row] = await this.db.select().from(reviews).where(eq(reviews.id, id));
    return row && toReview(row);
  }

  async exists(customerId: string, orderId: string, productId: string) {
    const [row] = await this.db
      .select({ id: reviews.id })
      .from(reviews)
      .where(and(eq(reviews.customerId, customerId), eq(reviews.orderId, orderId), eq(reviews.productId, productId)));
    return !!row;
  }

  async search(q: ReviewQuery) {
    const where: SQL[] = [];
    if (q.productId) where.push(eq(reviews.productId, q.productId));
    if (q.customerId) where.push(eq(reviews.customerId, q.customerId));
    if (q.rating) where.push(eq(reviews.rating, q.rating));
    if (q.withMedia) where.push(sql`cardinality(${reviews.mediaUrls}) > 0`);
    if (q.status !== 'ALL') where.push(eq(reviews.status, q.status ?? 'PUBLISHED'));
    const cond = where.length ? and(...where) : undefined;
    const order = {
      NEWEST: [desc(reviews.createdAt)],
      HELPFUL: [desc(reviews.helpfulCount), desc(reviews.createdAt)],
      RATING_HIGH: [desc(reviews.rating), desc(reviews.createdAt)],
      RATING_LOW: [asc(reviews.rating), desc(reviews.createdAt)],
    }[q.sort];
    const [rows, [total]] = await Promise.all([
      this.db
        .select()
        .from(reviews)
        .where(cond)
        .orderBy(...order)
        .limit(q.limit)
        .offset(q.offset),
      this.db.select({ n: count() }).from(reviews).where(cond),
    ]);
    return { items: rows.map(toReview), total: total?.n ?? 0 };
  }

  async insert(review: Review) {
    await this.db.insert(reviews).values(review.snapshot());
  }

  async update(review: Review, expectedVersion: number) {
    const { id, version: _v, createdAt: _c, helpfulCount: _h, ...rest } = review.snapshot();
    const rows = await this.db
      .update(reviews)
      .set({ ...rest, version: expectedVersion + 1 })
      .where(and(eq(reviews.id, id), eq(reviews.version, expectedVersion)))
      .returning({ id: reviews.id });
    return rows.length > 0;
  }

  async delete(id: string) {
    await this.db.delete(reviews).where(eq(reviews.id, id));
  }

  async addHelpfulVote(reviewId: string, userId: string) {
    const rows = await this.db
      .insert(helpfulVotes)
      .values({ reviewId, userId })
      .onConflictDoNothing()
      .returning({ reviewId: helpfulVotes.reviewId });
    if (rows.length === 0) return false;
    await this.db
      .update(reviews)
      .set({ helpfulCount: sql`${reviews.helpfulCount} + 1` })
      .where(eq(reviews.id, reviewId));
    return true;
  }
}

export class DrizzlePurchaseRepository implements PurchaseRepository {
  constructor(private readonly db: Conn) {}

  async upsertPlaced(orderId: string, customerId: string, productIds: readonly string[]) {
    if (productIds.length === 0) return;
    await this.db
      .insert(purchases)
      .values(productIds.map((productId) => ({ orderId, productId, customerId })))
      .onConflictDoNothing();
  }

  async setStatus(orderId: string, status: Purchase['status'], at: Date) {
    await this.db
      .update(purchases)
      .set({ status, confirmedAt: status === 'CONFIRMED' ? at : sql`${purchases.confirmedAt}` })
      .where(eq(purchases.orderId, orderId));
  }

  async find(customerId: string, orderId: string, productId: string) {
    const [row] = await this.db
      .select()
      .from(purchases)
      .where(
        and(eq(purchases.customerId, customerId), eq(purchases.orderId, orderId), eq(purchases.productId, productId)),
      );
    return row;
  }

  pendingReviews(customerId: string, limit: number) {
    return this.db
      .select({
        orderId: purchases.orderId,
        productId: purchases.productId,
        customerId: purchases.customerId,
        status: purchases.status,
        confirmedAt: purchases.confirmedAt,
      })
      .from(purchases)
      .leftJoin(
        reviews,
        and(
          eq(reviews.customerId, purchases.customerId),
          eq(reviews.orderId, purchases.orderId),
          eq(reviews.productId, purchases.productId),
        ),
      )
      .where(and(eq(purchases.customerId, customerId), eq(purchases.status, 'CONFIRMED'), isNull(reviews.id)))
      .orderBy(desc(purchases.confirmedAt))
      .limit(limit);
  }
}

type SummaryRow = typeof ratingSummaries.$inferSelect;
const toSummary = (r: SummaryRow): RatingSummary => ({
  productId: r.productId,
  stars: [r.star1, r.star2, r.star3, r.star4, r.star5],
});

export class DrizzleSummaryRepository implements SummaryRepository {
  constructor(private readonly db: Conn) {}

  async get(productId: string) {
    const [row] = await this.db.select().from(ratingSummaries).where(eq(ratingSummaries.productId, productId));
    return row ? toSummary(row) : emptySummary(productId);
  }

  async getMany(productIds: readonly string[]) {
    const map = new Map(productIds.map((id) => [id, emptySummary(id)]));
    if (productIds.length === 0) return map;
    const rows = await this.db
      .select()
      .from(ratingSummaries)
      .where(inArray(ratingSummaries.productId, [...productIds]));
    for (const r of rows) map.set(r.productId, toSummary(r));
    return map;
  }

  async lockAndGet(productId: string) {
    // Tạo dòng nếu chưa có rồi khoá: hai đánh giá đồng thời cho cùng sản phẩm không ghi đè nhau.
    await this.db.insert(ratingSummaries).values({ productId }).onConflictDoNothing();
    const [row] = await this.db
      .select()
      .from(ratingSummaries)
      .where(eq(ratingSummaries.productId, productId))
      .for('update');
    return toSummary(row!);
  }

  async save(s: RatingSummary) {
    const [star1, star2, star3, star4, star5] = s.stars;
    await this.db
      .update(ratingSummaries)
      .set({ star1, star2, star3, star4, star5, updatedAt: new Date() })
      .where(eq(ratingSummaries.productId, s.productId));
  }
}
