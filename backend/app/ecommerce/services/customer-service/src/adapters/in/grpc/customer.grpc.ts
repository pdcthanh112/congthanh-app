import { create } from '@bufbuild/protobuf';
import type { ConnectRouter } from '@connectrpc/connect';
import { DomainError } from '@ecommerce/platform';
import {
  CustomerService as CustomerGrpc,
  type ShippingAddress,
  ShippingAddressSchema,
} from '@ecommerce/proto/ecommerce/customer/v1/customer_service_pb';
import type { CustomerService } from '../../../application/customer.service.js';
import type { ShippingAddressProps } from '../../../domain/shipping-address.js';

const toProto = (a: ShippingAddressProps): ShippingAddress =>
  create(ShippingAddressSchema, {
    id: a.id,
    customerId: a.customerId,
    fullName: a.fullName,
    phoneNumber: a.phoneNumber,
    label: a.label,
    countryCode: a.countryCode,
    provinceCode: a.provinceCode ?? '',
    districtCode: a.districtCode ?? '',
    wardCode: a.wardCode ?? '',
    addressLine: a.addressLine,
    postalCode: a.postalCode ?? '',
    latitude: a.latitude ?? 0,
    longitude: a.longitude ?? 0,
    isDefault: a.isDefault,
  });

/** gRPC nội bộ cho order-service (mTLS trong mesh; không đi qua gateway). */
export function customerGrpcRoutes(customers: CustomerService) {
  return (router: ConnectRouter) => {
    router.service(CustomerGrpc, {
      async getShippingAddress(req) {
        const a = await customers.read.addresses.find(req.customerId, req.addressId);
        if (!a) throw DomainError.notFound(`address ${req.addressId} not found`);
        return toProto(a);
      },
      async getDefaultShippingAddress(req) {
        const a = await customers.read.addresses.findDefault(req.customerId);
        if (!a) throw DomainError.notFound(`customer ${req.customerId} has no default address`);
        return toProto(a);
      },
    });
  };
}
