package com.congthanh.productservice.application.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serial;
import java.io.Serializable;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ProductAttributeDTO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private long id;

    private String name;

}
