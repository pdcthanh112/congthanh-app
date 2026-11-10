import { create } from '@bufbuild/protobuf';
import { timestampFromDate } from '@bufbuild/protobuf/wkt';
import { type Database, DB, OutboxWriter } from '@ecom/platform';
import {
  WishlistItemAddedSchema,
  WishlistItemRemovedSchema,
} from '@ecom/proto/ecommerce/customer/v1/customer_events_pb';
import { Inject, Injectable } from '@nestjs/common';
import type { CustomerEvents, ReadRepositories, TransactionScope, UnitOfWork } from '../../../application/ports.js';
import {
  type Conn,
  DrizzleAddressRepository,
  DrizzleSearchHistoryRepository,
  DrizzleWishlistRepository,
} from './repositories.js';
import type * as schema from './schema.js';
import { outbox } from './schema.js';

class OutboxCustomerEvents implements CustomerEvents {
  constructor(
    private readonly tx: Conn,
    private readonly writer: OutboxWriter,
  ) {}

  async wishlistItemAdded(customerId: string, productId: string, at: Date) {
    await this.writer.write(this.tx, {
      aggregateType: 'customer',
      aggregateId: customerId,
      schema: WishlistItemAddedSchema,
      payload: create(WishlistItemAddedSchema, { customerId, productId, addedAt: timestampFromDate(at) }),
    });
  }

  async wishlistItemRemoved(customerId: string, productId: string) {
    await this.writer.write(this.tx, {
      aggregateType: 'customer',
      aggregateId: customerId,
      schema: WishlistItemRemovedSchema,
      payload: create(WishlistItemRemovedSchema, { customerId, productId }),
    });
  }
}

@Injectable()
export class DrizzleUnitOfWork implements UnitOfWork {
  private readonly writer = new OutboxWriter('customer-service', outbox);

  constructor(@Inject(DB) private readonly db: Database<typeof schema>) {}

  run<T>(work: (scope: TransactionScope) => Promise<T>): Promise<T> {
    return this.db.transaction((tx) =>
      work({
        addresses: new DrizzleAddressRepository(tx),
        wishlist: new DrizzleWishlistRepository(tx),
        searches: new DrizzleSearchHistoryRepository(tx),
        events: new OutboxCustomerEvents(tx, this.writer),
      }),
    );
  }
}

export function readRepositories(db: Database<typeof schema>): ReadRepositories {
  return {
    addresses: new DrizzleAddressRepository(db),
    wishlist: new DrizzleWishlistRepository(db),
    searches: new DrizzleSearchHistoryRepository(db),
  };
}
