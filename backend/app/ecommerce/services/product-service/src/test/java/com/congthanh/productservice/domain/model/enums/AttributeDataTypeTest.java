package com.congthanh.productservice.domain.model.enums;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AttributeDataTypeTest {

    @Test
    void normalisesByType() {
        assertThat(AttributeDataType.TEXT.normalize("  Paulo Coelho ").value()).isEqualTo("Paulo Coelho");
        var n = AttributeDataType.NUMBER.normalize("8,50");
        assertThat(n.value()).isEqualTo("8.5");
        assertThat(n.number()).isEqualByComparingTo(new BigDecimal("8.5"));
        assertThat(AttributeDataType.BOOLEAN.normalize("Có").value()).isEqualTo("true");
        assertThatThrownBy(() -> AttributeDataType.NUMBER.normalize("tám")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> AttributeDataType.TEXT.normalize(" ")).isInstanceOf(IllegalArgumentException.class);
    }
}
