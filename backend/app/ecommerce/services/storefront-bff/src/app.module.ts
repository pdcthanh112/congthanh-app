import { dirname, join } from 'node:path';
import { fileURLToPath } from 'node:url';
import {
  CartClient,
  CustomerClient,
  InventoryClient,
  OrderClient,
  ProductClient,
  PromotionClient,
} from '@ecom/clients';
import { PlatformAuthModule, PlatformHealthModule, PlatformLoggerModule, subgraphConfig } from '@ecom/platform';
import { type DynamicModule, Module } from '@nestjs/common';
import { GraphQLModule } from '@nestjs/graphql';
import { CartResolver } from './adapters/in/graphql/cart.resolver.js';
import { OrderResolver } from './adapters/in/graphql/order.resolver.js';
import { ProductResolver } from './adapters/in/graphql/product.resolver.js';
import { CategoryRef } from './adapters/in/graphql/types.js';
import {
  ADDRESSES,
  type AddressPort,
  CARTS,
  type CartPort,
  CURRENCY,
  INVENTORY,
  type InventoryPort,
  ORDERS,
  type OrderPort,
  PRODUCTS,
  PROMOTIONS,
  type ProductPort,
  type PromotionPort,
} from './application/ports.js';
import { StorefrontService } from './application/storefront.service.js';
import type { Env } from './config/env.js';

export interface Backends {
  products: ProductPort;
  inventory: InventoryPort;
  carts: CartPort;
  promotions: PromotionPort;
  orders: OrderPort;
  addresses: AddressPort;
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
    addresses: new CustomerClient(env.CUSTOMER_GRPC_ADDR),
  };
}

const serviceDir = join(dirname(fileURLToPath(import.meta.url)), '..');

/** BFF không có DB: mọi dữ liệu lấy từ service phía sau qua gRPC/REST (adapter trong @ecom/clients). */
@Module({})
export class AppModule {
  /** backends: thay client thật bằng bản giả trong test (composition root duy nhất). */
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
        StorefrontService,
        ProductResolver,
        CartResolver,
        OrderResolver,
        { provide: CURRENCY, useValue: env.CURRENCY },
        { provide: PRODUCTS, useFactory: () => b().products },
        { provide: INVENTORY, useFactory: () => b().inventory },
        { provide: CARTS, useFactory: () => b().carts },
        { provide: PROMOTIONS, useFactory: () => b().promotions },
        { provide: ORDERS, useFactory: () => b().orders },
        { provide: ADDRESSES, useFactory: () => b().addresses },
      ],
    };
  }
}
