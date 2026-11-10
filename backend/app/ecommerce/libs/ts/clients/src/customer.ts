import { bearer, fromGrpcError, grpcClient, resiliencePolicy } from '@ecom/platform';
import { CustomerService, type ShippingAddress } from '@ecom/proto/ecommerce/customer/v1/customer_service_pb';

/** customer-service (NestJS) qua gRPC: lấy địa chỉ giao hàng của khách để chụp vào đơn. */
export class CustomerClient {
  private readonly grpc;
  private readonly policy = resiliencePolicy({ timeoutMs: 1_500 });

  constructor(address: string) {
    this.grpc = grpcClient(CustomerService, { address });
  }

  async shippingAddress(customerId: string, addressId?: string | null): Promise<ShippingAddress> {
    try {
      return await this.policy.execute(({ signal }) =>
        addressId
          ? this.grpc.getShippingAddress({ customerId, addressId }, bearer(undefined, { signal }))
          : this.grpc.getDefaultShippingAddress({ customerId }, bearer(undefined, { signal })),
      );
    } catch (e) {
      throw fromGrpcError(e, 'customer-service');
    }
  }
}
