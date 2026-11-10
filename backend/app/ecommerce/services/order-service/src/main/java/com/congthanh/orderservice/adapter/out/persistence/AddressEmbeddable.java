package com.congthanh.orderservice.adapter.out.persistence;

import com.congthanh.orderservice.domain.model.Address;
import jakarta.persistence.Embeddable;

@Embeddable
public class AddressEmbeddable {

    private String fullName;
    private String phone;
    private String line1;
    private String line2;
    private String ward;
    private String district;
    private String city;
    private String country;
    private String postalCode;

    protected AddressEmbeddable() {
    }

    static AddressEmbeddable from(Address a) {
        if (a == null) {
            return null;
        }
        AddressEmbeddable e = new AddressEmbeddable();
        e.fullName = a.fullName();
        e.phone = a.phone();
        e.line1 = a.line1();
        e.line2 = a.line2();
        e.ward = a.ward();
        e.district = a.district();
        e.city = a.city();
        e.country = a.country();
        e.postalCode = a.postalCode();
        return e;
    }

    Address toDomain() {
        if (fullName == null) {
            return null;
        }
        return new Address(fullName, phone, line1, line2, ward, district, city, country, postalCode);
    }
}
