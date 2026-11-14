package com.congthanh.checkoutservice.adapter.out.grpc;

import com.congthanh.checkoutservice.application.port.out.Customers;
import com.congthanh.checkoutservice.domain.model.Address;
import com.congthanh.customerservice.grpc.CustomerServiceGrpc;
import com.congthanh.customerservice.grpc.GetDefaultShippingAddressRequest;
import com.congthanh.customerservice.grpc.GetShippingAddressRequest;
import com.congthanh.customerservice.grpc.ShippingAddress;
import io.github.resilience4j.retry.annotation.Retry;
import io.grpc.StatusRuntimeException;
import org.springframework.grpc.client.GrpcChannelFactory;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;

@Component
class CustomersGrpcAdapter implements Customers {

    private final CustomerServiceGrpc.CustomerServiceBlockingStub stub;

    CustomersGrpcAdapter(GrpcChannelFactory channels) {
        this.stub = CustomerServiceGrpc.newBlockingStub(channels.createChannel("customer"));
    }

    /** addressId trống: dùng địa chỉ mặc định của khách. */
    @Override
    @Retry(name = "customer")
    public Address shippingAddress(String customerId, String addressId) {
        try {
            var s = stub.withDeadlineAfter(2, TimeUnit.SECONDS);
            ShippingAddress a = addressId == null || addressId.isBlank()
                    ? s.getDefaultShippingAddress(GetDefaultShippingAddressRequest.newBuilder().setCustomerId(customerId).build())
                    : s.getShippingAddress(GetShippingAddressRequest.newBuilder().setCustomerId(customerId)
                    .setAddressId(addressId).build());
            // Mỹ: city mang mã bang (thuế/phí ship theo bang), như địa chỉ khách nhập ở checkout.
            return new Address(a.getFullName(), a.getPhoneNumber(), a.getAddressLine(), null, blank(a.getWardCode()),
                    blank(a.getDistrictCode()), a.getProvinceCode(), a.getCountryCode(), blank(a.getPostalCode()));
        } catch (StatusRuntimeException e) {
            throw Protos.translate("customer-service", e);
        }
    }

    private static String blank(String s) {
        return s == null || s.isBlank() ? null : s;
    }
}
