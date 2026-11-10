package com.congthanh.orderservice.domain.model;

import com.congthanh.orderservice.domain.exception.OrderException;

public record Address(String fullName, String phone, String line1, String line2,
                      String ward, String district, String city, String country, String postalCode) {

    public Address {
        if (isBlank(fullName) || isBlank(phone) || isBlank(line1) || isBlank(city) || isBlank(country)) {
            throw OrderException.invalid("address requires fullName, phone, line1, city and country");
        }
    }

    private static boolean isBlank(String s) {
        return s == null || s.isBlank();
    }
}
