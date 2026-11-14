package com.congthanh.checkoutservice.application.port.in;

import com.congthanh.checkoutservice.domain.model.CheckoutSession;

import java.math.BigDecimal;
import java.util.List;

/**
 * Đặt đơn gia hạn cho subscription-service, không có khách ngồi trước màn hình (ADR 0027): báo giá theo giá hiện hành,
 * chọn cách giao rẻ nhất, áp giảm giá subscriber, charge off-session bằng phương thức đã lưu. Idempotent theo
 * {@code idempotencyKey} (subscription:kỳ:lượt thử): gọi lại trả đúng đơn cũ.
 */
public interface SubscriptionOrders {

    Result place(Command command);

    /**
     * @param paymentMethodRef phương thức đã lưu dạng {@code cus_...:pm_...} (Stripe Customer + PaymentMethod từ
     *                         SetupIntent); sandbox nhận chuỗi bất kỳ
     * @param promotionCode    mã khuyến mãi dành cho subscriber (Subscribe &amp; Save), trống = không giảm
     */
    record Command(String idempotencyKey, String subscriptionId, String customerId, List<CheckoutSession.Item> items,
                   String addressId, String paymentMethodRef, String promotionCode) {
    }

    record Result(String orderId, String orderCode, BigDecimal total, String currency) {
    }
}
