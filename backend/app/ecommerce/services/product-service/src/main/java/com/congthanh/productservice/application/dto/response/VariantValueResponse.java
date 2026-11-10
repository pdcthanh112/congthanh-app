package com.congthanh.productservice.application.dto.response;

import java.util.List;

public record VariantValueResponse(String title, List<OptionValue> option) {
}
