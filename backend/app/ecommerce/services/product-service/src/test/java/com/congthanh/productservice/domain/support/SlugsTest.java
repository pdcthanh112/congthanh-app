package com.congthanh.productservice.domain.support;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SlugsTest {

    @Test
    void vietnameseNameBecomesAsciiSlugIncludingDStroke() {
        assertThat(Slugs.from("Đồng hồ Rolex GMT-Master II ")).isEqualTo("dong-ho-rolex-gmt-master-ii");
        assertThat(Slugs.from("Iphone 16 Pro Max Đen 128GB")).isEqualTo("iphone-16-pro-max-den-128gb");
    }

    @Test
    void validatesFormat() {
        assertThat(Slugs.isValid("nha-gia-kim")).isTrue();
        assertThat(Slugs.isValid("Nha-Gia-Kim")).isFalse();
        assertThat(Slugs.isValid("nha--gia")).isFalse();
        assertThat(Slugs.isValid("-nha")).isFalse();
    }
}
