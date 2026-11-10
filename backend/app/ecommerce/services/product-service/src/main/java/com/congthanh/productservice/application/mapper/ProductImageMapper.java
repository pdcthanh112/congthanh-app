package com.congthanh.productservice.application.mapper;

import com.congthanh.productservice.domain.model.ProductImage;
import com.congthanh.productservice.application.dto.ProductImageDTO;
import jakarta.annotation.PostConstruct;
import org.modelmapper.ModelMapper;
import org.modelmapper.convention.MatchingStrategies;
import org.springframework.stereotype.Component;

@Component
public class ProductImageMapper {

    private static final ModelMapper modelMapper = new ModelMapper();

    static {
        modelMapper.getConfiguration()
                .setMatchingStrategy(MatchingStrategies.STRICT)
                .setFieldMatchingEnabled(true)
                .setSkipNullEnabled(true);
    }

    @PostConstruct
    private void configureModelMapper() {
        modelMapper.typeMap(ProductImage.class, ProductImageDTO.class)
                .addMapping(src -> src.getProduct().getId(), ProductImageDTO::setProduct);
    }

    public static ProductImage mapProductImageDTOToEntity(ProductImageDTO productImageDTO) {
        return modelMapper.map(productImageDTO, ProductImage.class);
    }

    public static ProductImageDTO mapProductImageEntityToDTO(ProductImage productImage) {
        return modelMapper.map(productImage, ProductImageDTO.class);
    }

}
