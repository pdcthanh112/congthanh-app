import { create } from '@bufbuild/protobuf';
import { timestampFromDate } from '@bufbuild/protobuf/wkt';
import { type Database, DB, markProcessed, OutboxWriter } from '@ecom/platform';
import {
  ProductRatingChangedSchema,
  ReviewModeratedSchema,
  ReviewRepliedSchema,
  ReviewSubmittedSchema,
} from '@ecom/proto/ecommerce/review/v1/review_events_pb';
import { Inject, Injectable } from '@nestjs/common';
import type { ReadRepositories, ReviewEvents, TransactionScope, UnitOfWork } from '../../../application/ports.js';
import { averageOf, countOf, type RatingSummary } from '../../../domain/rating-summary.js';
import type { Review } from '../../../domain/review.js';
import {
  type Conn,
  DrizzlePurchaseRepository,
  DrizzleReviewRepository,
  DrizzleSummaryRepository,
} from './repositories.js';
import type * as schema from './schema.js';
import { outbox, processedEvents } from './schema.js';

class OutboxReviewEvents implements ReviewEvents {
  constructor(
    private readonly tx: Conn,
    private readonly writer: OutboxWriter,
  ) {}

  async submitted(review: Review) {
    const r = review.snapshot();
    await this.writer.write(this.tx, {
      aggregateType: 'review',
      aggregateId: r.productId,
      schema: ReviewSubmittedSchema,
      payload: create(ReviewSubmittedSchema, {
        reviewId: r.id,
        productId: r.productId,
        customerId: r.customerId,
        orderId: r.orderId,
        rating: r.rating,
        submittedAt: timestampFromDate(r.createdAt),
      }),
    });
  }

  async moderated(review: Review, moderatorId: string) {
    const r = review.snapshot();
    await this.writer.write(this.tx, {
      aggregateType: 'review',
      aggregateId: r.productId,
      schema: ReviewModeratedSchema,
      payload: create(ReviewModeratedSchema, {
        reviewId: r.id,
        productId: r.productId,
        status: r.status,
        reason: r.moderationReason ?? '',
        moderatorId,
      }),
    });
  }

  async replied(review: Review, sellerId: string) {
    const r = review.snapshot();
    await this.writer.write(this.tx, {
      aggregateType: 'review',
      aggregateId: r.productId,
      schema: ReviewRepliedSchema,
      payload: create(ReviewRepliedSchema, {
        reviewId: r.id,
        productId: r.productId,
        customerId: r.customerId,
        sellerId,
      }),
    });
  }

  async ratingChanged(s: RatingSummary) {
    await this.writer.write(this.tx, {
      aggregateType: 'review',
      aggregateId: s.productId,
      schema: ProductRatingChangedSchema,
      payload: create(ProductRatingChangedSchema, {
        productId: s.productId,
        reviewCount: BigInt(countOf(s)),
        averageRating: averageOf(s),
        starCounts: s.stars.map(BigInt),
      }),
    });
  }
}

@Injectable()
export class DrizzleUnitOfWork implements UnitOfWork {
  private readonly writer = new OutboxWriter('review-service', outbox);

  constructor(@Inject(DB) private readonly db: Database<typeof schema>) {}

  run<T>(work: (scope: TransactionScope) => Promise<T>): Promise<T> {
    return this.db.transaction((tx) =>
      work({
        reviews: new DrizzleReviewRepository(tx),
        purchases: new DrizzlePurchaseRepository(tx),
        summaries: new DrizzleSummaryRepository(tx),
        events: new OutboxReviewEvents(tx, this.writer),
        markProcessed: (eventId, consumer) => markProcessed(tx, processedEvents, eventId, consumer),
      }),
    );
  }
}

export function readRepositories(db: Database<typeof schema>): ReadRepositories {
  return {
    reviews: new DrizzleReviewRepository(db),
    purchases: new DrizzlePurchaseRepository(db),
    summaries: new DrizzleSummaryRepository(db),
  };
}
