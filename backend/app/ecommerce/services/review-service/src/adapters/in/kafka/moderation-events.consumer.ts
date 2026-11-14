import { KafkaEventConsumer } from '@ecommerce/platform';
import { ModerationDecidedSchema } from '@ecommerce/proto/ecommerce/moderation/v1/moderation_events_pb';
import type { ReviewService } from '../../../application/review.service.js';

type Decision = 'APPROVE' | 'REJECT' | 'REDACT' | 'ESCALATE';
const DECISIONS = new Set<string>(['APPROVE', 'REJECT', 'REDACT', 'ESCALATE']);

/**
 * Nghe ecommerce.moderation.events: quyết định của người duyệt (hàng chờ, khiếu nại) cho đánh giá. Quyết định tự động
 * (decidedBy = "auto") đã áp đồng bộ lúc gửi nên bỏ qua.
 */
export function moderationEventsConsumer(reviews: ReviewService, brokers: string[], clientId: string) {
  return new KafkaEventConsumer({
    brokers,
    clientId: `${clientId}-moderation`,
    groupId: 'review-service-moderation',
    topics: ['ecommerce.moderation.events'],
  }).on(ModerationDecidedSchema, async (e, env) => {
    if (e.contentType !== 'REVIEW' || e.decidedBy === 'auto' || !DECISIONS.has(e.decision)) return;
    await reviews.applyModerationDecision(env.id, e.contentId, e.decision as Decision, e.redactedText, e.decidedBy);
  });
}
