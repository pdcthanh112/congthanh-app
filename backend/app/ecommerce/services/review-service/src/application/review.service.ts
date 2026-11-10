import { DomainError } from '@ecom/platform/errors';
import { Inject, Injectable } from '@nestjs/common';
import { applyChange } from '../domain/rating-summary.js';
import { Review, type ReviewDraft, type ReviewStatus } from '../domain/review.js';
import {
  CLOCK,
  type Clock,
  ID_GENERATOR,
  type IdGenerator,
  PRODUCT_DIRECTORY,
  type ProductDirectory,
  READ_REPOSITORIES,
  REVIEW_SETTINGS,
  type ReadRepositories,
  type ReviewQuery,
  type ReviewSettings,
  type TransactionScope,
  UNIT_OF_WORK,
  type UnitOfWork,
} from './ports.js';

export interface Author {
  id: string;
  name: string;
}

/** Điểm chỉ tính đánh giá đang hiển thị. */
const counted = (r: Review | undefined) => (r && r.status === 'PUBLISHED' ? r.rating : null);

@Injectable()
export class ReviewService {
  constructor(
    @Inject(UNIT_OF_WORK) private readonly uow: UnitOfWork,
    @Inject(READ_REPOSITORIES) readonly read: ReadRepositories,
    @Inject(PRODUCT_DIRECTORY) private readonly products: ProductDirectory,
    @Inject(CLOCK) private readonly clock: Clock,
    @Inject(ID_GENERATOR) private readonly ids: IdGenerator,
    @Inject(REVIEW_SETTINGS) private readonly settings: ReviewSettings,
  ) {}

  search(q: ReviewQuery) {
    return this.read.reviews.search({ ...q, limit: Math.min(Math.max(q.limit, 1), 50), offset: Math.max(q.offset, 0) });
  }

  /** Chỉ được đánh giá sản phẩm trong đơn đã thanh toán, mỗi sản phẩm mỗi đơn một lần. */
  submit(author: Author, orderId: string, productId: string, draft: ReviewDraft): Promise<Review> {
    return this.uow.run(async (tx) => {
      const purchase = await tx.purchases.find(author.id, orderId, productId);
      if (purchase?.status !== 'CONFIRMED') {
        throw DomainError.forbidden('only purchased products can be reviewed', 'NOT_PURCHASED');
      }
      if (await tx.reviews.exists(author.id, orderId, productId)) {
        throw DomainError.conflict('you already reviewed this product for this order', 'ALREADY_REVIEWED');
      }
      const review = Review.submit(
        { id: this.ids.next(), productId, customerId: author.id, authorName: author.name, orderId },
        draft,
        this.settings.mediaUrlPrefix,
        this.clock.now(),
      );
      await tx.reviews.insert(review);
      await tx.events.submitted(review);
      await this.adjustSummary(tx, productId, null, counted(review));
      return review;
    });
  }

  edit(customerId: string, id: string, version: number, draft: ReviewDraft): Promise<Review> {
    return this.mutate(id, version, async (review, before, tx) => {
      review.edit(customerId, draft, this.settings.mediaUrlPrefix, this.clock.now());
      return async () => this.adjustSummary(tx, review.productId, before, counted(review));
    });
  }

  remove(customerId: string, id: string): Promise<boolean> {
    return this.uow.run(async (tx) => {
      const review = await tx.reviews.findById(id);
      if (!review) return false;
      review.assertAuthor(customerId);
      await tx.reviews.delete(id);
      await this.adjustSummary(tx, review.productId, counted(review), null);
      return true;
    });
  }

  /** Seller chỉ trả lời đánh giá sản phẩm của mình (hỏi product-service ai là chủ). */
  async reply(sellerId: string, isAdmin: boolean, id: string, version: number, text: string): Promise<Review> {
    const current = await this.read.reviews.findById(id);
    if (!current) throw DomainError.notFound(`review ${id} not found`);
    if (!isAdmin && (await this.products.sellerOf(current.productId)) !== sellerId) {
      throw DomainError.forbidden('only the seller of this product can reply');
    }
    return this.mutate(id, version, async (review, _before, tx) => {
      review.reply(text, this.clock.now());
      return async () => tx.events.replied(review, sellerId);
    });
  }

  moderate(moderatorId: string, id: string, version: number, status: ReviewStatus, reason: string | null) {
    return this.mutate(id, version, async (review, before, tx) => {
      review.moderate(status, reason, this.clock.now());
      return async () => {
        await tx.events.moderated(review, moderatorId);
        await this.adjustSummary(tx, review.productId, before, counted(review));
      };
    });
  }

  voteHelpful(userId: string, id: string): Promise<boolean> {
    return this.uow.run(async (tx) => {
      const review = await tx.reviews.findById(id);
      if (review?.status !== 'PUBLISHED') throw DomainError.notFound(`review ${id} not found`);
      if (review.snapshot().customerId === userId) throw DomainError.invalid('cannot vote for your own review');
      return tx.reviews.addHelpfulVote(id, userId);
    });
  }

  pendingReviews(customerId: string, limit: number) {
    return this.read.purchases.pendingReviews(customerId, Math.min(Math.max(limit, 1), 50));
  }

  // ---------- nội bộ ----------

  private mutate(
    id: string,
    version: number,
    change: (review: Review, ratingBefore: number | null, tx: TransactionScope) => Promise<() => Promise<void>>,
  ): Promise<Review> {
    return this.uow.run(async (tx) => {
      const review = await tx.reviews.findById(id);
      if (!review) throw DomainError.notFound(`review ${id} not found`);
      if (review.snapshot().version !== version) {
        throw DomainError.conflict('review was modified; reload and retry', 'STALE_VERSION');
      }
      const before = counted(review);
      const after = await change(review, before, tx);
      if (!(await tx.reviews.update(review, version))) {
        throw DomainError.conflict('review was modified; reload and retry', 'STALE_VERSION');
      }
      await after();
      return Review.rehydrate({ ...review.snapshot(), version: version + 1 });
    });
  }

  private async adjustSummary(tx: TransactionScope, productId: string, before: number | null, after: number | null) {
    if (before === after) return;
    const summary = applyChange(await tx.summaries.lockAndGet(productId), before, after);
    await tx.summaries.save(summary);
    await tx.events.ratingChanged(summary);
  }
}
