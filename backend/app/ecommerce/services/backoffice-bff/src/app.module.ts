import { dirname, join } from 'node:path';
import { fileURLToPath } from 'node:url';
import { CartClient, InventoryClient, OrderClient, ProductClient, PromotionClient } from '@ecommerce/clients';
import { PlatformAuthModule, PlatformHealthModule, PlatformLoggerModule, subgraphConfig } from '@ecommerce/platform';
import { type DynamicModule, Module } from '@nestjs/common';
import { GraphQLModule } from '@nestjs/graphql';
import { OperationsResolver } from './adapters/in/graphql/operations.resolver.js';
import { ProductResolver } from './adapters/in/graphql/product.resolver.js';
import { CategoryRef } from './adapters/in/graphql/types.js';
import { BackofficeService } from './application/backoffice.service.js';
import {
  CARTS,
  type CartPort,
  INVENTORY,
  type InventoryPort,
  ORDERS,
  type OrderPort,
  PRODUCTS,
  PROMOTIONS,
  type ProductPort,
  type PromotionPort,
} from './application/ports.js';
import type { Env } from './config/env.js';

export interface Backends {
  products: ProductPort;
  inventory: InventoryPort;
  carts: CartPort;
  promotions: PromotionPort;
  orders: OrderPort;
}

export function realBackends(env: Env): Backends {
  return {
    products: new ProductClient({
      grpcAddress: env.PRODUCT_GRPC_ADDR,
      restBaseUrl: env.PRODUCT_REST_URL,
      currency: env.CURRENCY,
    }),
    inventory: new InventoryClient(env.INVENTORY_GRPC_ADDR),
    carts: new CartClient({ grpcAddress: env.CART_GRPC_ADDR, restBaseUrl: env.CART_REST_URL }),
    promotions: new PromotionClient(env.PROMOTION_GRPC_ADDR),
    orders: new OrderClient(env.ORDER_REST_URL),
  };
}

const serviceDir = join(dirname(fileURLToPath(import.meta.url)), '..');

/** BFF quản trị: không có DB; gom product/inventory/cart/promotion/order cho trang admin và người bán. */
@Module({})
export class AppModule {
  /** backends: thay client thật bằng bản giả trong test. */
  static forRoot(env: Env, backends?: Backends): DynamicModule {
    let cached = backends;
    const b = () => {
      cached ??= realBackends(env);
      return cached;
    };
    return {
      module: AppModule,
      imports: [
        PlatformLoggerModule.forRoot({ serviceName: env.SERVICE_NAME, level: env.LOG_LEVEL, format: env.LOG_FORMAT }),
        PlatformAuthModule.forRoot({
          issuer: env.OIDC_ISSUER_URI,
          jwksUrl: env.JWT_JWKS_URL,
          audience: env.JWT_AUDIENCE,
        }),
        PlatformHealthModule.forRoot({ withPostgres: false }),
        GraphQLModule.forRoot(
          subgraphConfig({
            serviceDir,
            production: env.NODE_ENV === 'production',
            extra: { buildSchemaOptions: { orphanedTypes: [CategoryRef] } },
          }),
        ),
      ],
      providers: [
        BackofficeService,
        ProductResolver,
        OperationsResolver,
        { provide: PRODUCTS, useFactory: () => b().products },
        { provide: INVENTORY, useFactory: () => b().inventory },
        { provide: CARTS, useFactory: () => b().carts },
        { provide: PROMOTIONS, useFactory: () => b().promotions },
        { provide: ORDERS, useFactory: () => b().orders },
      ],
    };
  }
}
