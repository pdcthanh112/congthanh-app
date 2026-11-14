package com.congthanh.disputeservice.domain.model;

import java.util.Locale;

/** Lý do tranh chấp chuẩn hoá từ mã của cổng (Stripe: fraudulent, product_not_received...). */
public enum DisputeReason {
    FRAUDULENT, PRODUCT_NOT_RECEIVED, PRODUCT_UNACCEPTABLE, DUPLICATE, SUBSCRIPTION_CANCELED, CREDIT_NOT_PROCESSED,
    GENERAL;

    public static DisputeReason fromProvider(String code) {
        if (code == null) {
            return GENERAL;
        }
        return switch (code.toLowerCase(Locale.ROOT)) {
            case "fraudulent", "unrecognized" -> FRAUDULENT;
            case "product_not_received" -> PRODUCT_NOT_RECEIVED;
            case "product_unacceptable" -> PRODUCT_UNACCEPTABLE;
            case "duplicate" -> DUPLICATE;
            case "subscription_canceled" -> SUBSCRIPTION_CANCELED;
            case "credit_not_processed" -> CREDIT_NOT_PROCESSED;
            default -> GENERAL;
        };
    }
}
