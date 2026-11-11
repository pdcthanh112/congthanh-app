package com.congthanh.paymentservice.domain.model;

/**
 * Phương thức khách chọn ở checkout.
 * CARD, APPLE_PAY, GOOGLE_PAY: qua Stripe (Payment Element tạo ConfirmationToken; ví điện tử là thẻ đã token hoá).
 * AFFIRM, KLARNA, AFTERPAY: trả góp (pay later) qua Stripe, đối tác cho vay và chịu rủi ro tín dụng (ADR 0016).
 * PAYPAL: qua PayPal Orders API v2; PayPal Pay Later là nguồn tiền bên trong luồng này, không phải method riêng.
 */
public enum PaymentMethod {
    CARD(Category.CARD, "card"),
    APPLE_PAY(Category.WALLET, "card"),
    GOOGLE_PAY(Category.WALLET, "card"),
    PAYPAL(Category.PAYPAL, null),
    AFFIRM(Category.INSTALLMENT, "affirm"),
    KLARNA(Category.INSTALLMENT, "klarna"),
    AFTERPAY(Category.INSTALLMENT, "afterpay_clearpay");

    /** Nhóm để fraud, báo cáo và luật nghiệp vụ không phải liệt kê từng đối tác. */
    public enum Category { CARD, WALLET, PAYPAL, INSTALLMENT }

    private final Category category;
    private final String stripeType;

    PaymentMethod(Category category, String stripeType) {
        this.category = category;
        this.stripeType = stripeType;
    }

    public Category category() {
        return category;
    }

    public boolean isInstallment() {
        return category == Category.INSTALLMENT;
    }

    /** Loại PaymentMethod phía Stripe (ConfirmationToken.payment_method_preview.type); null nếu không qua Stripe. */
    public String stripeType() {
        return stripeType;
    }
}
