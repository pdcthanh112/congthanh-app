package com.congthanh.productservice.application.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serial;
import java.io.Serializable;

/** Ảnh sản phẩm; {@code id} là id media do media-service cấp. */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ProductImageDTO implements Serializable {

    @Serial
    private static final long serialVersionUID = 2L;

    private Long id;

    private String product;

    private String imagePath;

    private int displayOrder;

    @JsonProperty("isDefault")
    private boolean isDefault;
}
