package com.congthanh.orderservice.application.port.in;

import com.congthanh.orderservice.domain.model.Address;
import com.congthanh.orderservice.domain.model.Money;
import com.congthanh.orderservice.domain.model.OrderStatus;
import com.congthanh.orderservice.domain.model.PaymentMethod;

import java.util.List;
import java.util.Map;

/** Tạo đơn từ giỏ hàng và khởi động saga checkout. */
public interface PlaceOrderUseCase {

    PlacedOrder place(PlaceOrderCommand command);

    record Item(String productId, int quantity) {
    }

    /**
     * @param priced giá đã chốt bởi checkout-service (gọi nội bộ qua gRPC); null = luồng cũ, lấy giá từ catalog.
     */
    record PlaceOrderCommand(String customerId, List<Item> items, String promotionCode,
                             PaymentMethod paymentMethod, String paymentToken, String returnUrl, String clientIp,
                             Address shippingAddress, Address billingAddress, String note, Priced priced) {

        public PlaceOrderCommand(String customerId, List<Item> items, String promotionCode,
                                 PaymentMethod paymentMethod, String paymentToken, String returnUrl, String clientIp,
                                 Address shippingAddress, Address billingAddress, String note) {
            this(customerId, items, promotionCode, paymentMethod, paymentToken, returnUrl, clientIp,
                    shippingAddress, billingAddress, note, null);
        }
    }

    /**
     * Báo giá từ checkout-service: order id do checkout cấp (gọi lại cùng id trả về đơn đã có, không tạo đơn
     * thứ hai), đơn giá theo pricing-service, phí giao hàng và thuế.
     */
    record Priced(String orderId, Map<String, Money> unitPrices, Money shippingFee, Money tax) {
    }

    record PlacedOrder(String orderId, String orderCode, OrderStatus status, Money total, Money discount) {
    }
}
