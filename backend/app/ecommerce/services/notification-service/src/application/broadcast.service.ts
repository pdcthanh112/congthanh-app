import { DomainError } from '@ecommerce/platform/errors';
import { Inject, Injectable } from '@nestjs/common';
import { type Broadcast, type BroadcastDraft, requireCancellable, scheduleBroadcast } from '../domain/broadcast.js';
import {
  CLOCK,
  type Clock,
  type DeliveryCount,
  ID_GENERATOR,
  type IdGenerator,
  READ_REPOSITORIES,
  type ReadRepositories,
  UNIT_OF_WORK,
  type UnitOfWork,
} from './ports.js';

const MAX_IDEMPOTENCY_KEY_LENGTH = 100;

/** Use case của admin cho chiến dịch gửi hàng loạt (ADR 0034); việc chia trang và gửi do worker làm. */
@Injectable()
export class BroadcastService {
  constructor(
    @Inject(UNIT_OF_WORK) private readonly uow: UnitOfWork,
    @Inject(READ_REPOSITORIES) private readonly read: ReadRepositories,
    @Inject(CLOCK) private readonly clock: Clock,
    @Inject(ID_GENERATOR) private readonly ids: IdGenerator,
  ) {}

  /**
   * Tạo chiến dịch (gửi ngay hoặc hẹn giờ). Idempotent theo (người tạo, khoá): bấm gửi hai lần hoặc client thử lại
   * chỉ ra một chiến dịch, lần sau trả chiến dịch đã có.
   */
  async create(draft: BroadcastDraft, createdBy: string, idempotencyKey: string): Promise<Broadcast> {
    const key = idempotencyKey.trim();
    if (!key) throw DomainError.invalid('idempotency key is required');
    if (key.length > MAX_IDEMPOTENCY_KEY_LENGTH) {
      throw DomainError.invalid(`idempotency key must be at most ${MAX_IDEMPOTENCY_KEY_LENGTH} characters`);
    }
    const broadcast = scheduleBroadcast(this.ids.next(), draft, createdBy, this.clock.now());
    return this.read.broadcasts.insertOrGet(broadcast, key);
  }

  /** Huỷ chiến dịch chưa xong: dừng chia trang và bỏ các lượt gửi còn chờ. Thông báo in-app đã tạo được giữ. */
  cancel(id: string): Promise<Broadcast> {
    return this.uow.run(async (tx) => {
      const broadcast = await tx.broadcasts.lock(id);
      if (!broadcast) throw DomainError.notFound(`broadcast ${id} not found`);
      requireCancellable(broadcast);
      const now = this.clock.now();
      await tx.broadcasts.cancel(id, now);
      return { ...broadcast, status: 'CANCELLED', cancelledAt: now };
    });
  }

  async get(id: string): Promise<Broadcast> {
    const broadcast = await this.read.broadcasts.get(id);
    if (!broadcast) throw DomainError.notFound(`broadcast ${id} not found`);
    return broadcast;
  }

  list(page: number, size: number) {
    const limit = Math.min(Math.max(size, 1), 50);
    return this.read.broadcasts.list(limit, (Math.max(page, 1) - 1) * limit);
  }

  /** Tiến độ gửi theo kênh và trạng thái. */
  deliveryCounts(id: string): Promise<DeliveryCount[]> {
    return this.read.broadcasts.deliveryCounts(id);
  }
}
