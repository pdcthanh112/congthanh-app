import { type Database, DB, markProcessed } from '@ecommerce/platform';
import { Inject, Injectable } from '@nestjs/common';
import type { ReadRepositories, TransactionScope, UnitOfWork } from '../../../application/ports.js';
import {
  DrizzleDeliveryQueue,
  DrizzleDeviceRepository,
  DrizzleLocalViews,
  DrizzleNotificationRepository,
  DrizzlePreferenceRepository,
} from './repositories.js';
import type * as schema from './schema.js';
import { processedEvents } from './schema.js';

@Injectable()
export class DrizzleUnitOfWork implements UnitOfWork {
  constructor(@Inject(DB) private readonly db: Database<typeof schema>) {}

  run<T>(work: (scope: TransactionScope) => Promise<T>): Promise<T> {
    return this.db.transaction((tx) =>
      work({
        notifications: new DrizzleNotificationRepository(tx),
        preferences: new DrizzlePreferenceRepository(tx),
        views: new DrizzleLocalViews(tx),
        markProcessed: (eventId, consumer) => markProcessed(tx, processedEvents, eventId, consumer),
      }),
    );
  }
}

export function readRepositories(db: Database<typeof schema>): ReadRepositories {
  return {
    notifications: new DrizzleNotificationRepository(db),
    preferences: new DrizzlePreferenceRepository(db),
    devices: new DrizzleDeviceRepository(db),
    deliveries: new DrizzleDeliveryQueue(db),
  };
}
