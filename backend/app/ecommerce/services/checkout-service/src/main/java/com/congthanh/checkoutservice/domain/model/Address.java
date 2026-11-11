package com.congthanh.checkoutservice.domain.model;

import com.congthanh.checkoutservice.domain.exception.CheckoutException;

import java.util.Locale;

/** Địa chỉ giao hàng. {@code country} là mã ISO 3166-1 alpha-2, {@code city} là mã tỉnh/thành. */
public record Address(String fullName, String phone, String line1, String line2, String ward, String district,
                      String city, String country, String postalCode) {

    public Address {
        if (blank(fullName) || blank(phone) || blank(line1) || blank(city) || blank(country)) {
            throw CheckoutException.invalid("address requires fullName, phone, line1, city and country");
        }
        country = country.trim().toUpperCase(Locale.ROOT);
        if (country.length() != 2) {
            throw CheckoutException.invalid("country must be an ISO 3166-1 alpha-2 code");
        }
    }

    private static boolean blank(String s) {
        return s == null || s.isBlank();
    }
}
