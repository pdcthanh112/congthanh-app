package com.congthanh.productservice.application.dto.request;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CreateVariantImageRequest {

    private String imagePath;

    private String alt;

    @JsonProperty("isDefault")
    private boolean isDefault;

}
