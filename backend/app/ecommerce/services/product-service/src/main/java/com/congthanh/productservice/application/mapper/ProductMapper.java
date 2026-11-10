package com.congthanh.productservice.application.mapper;

import com.congthanh.productservice.domain.model.ProductImage;
import com.congthanh.productservice.application.dto.ProductAttributeValueDTO;
import com.congthanh.productservice.application.dto.ProductDTO;
import com.congthanh.productservice.application.dto.ProductImageDTO;
import com.congthanh.productservice.domain.model.Product;
import com.congthanh.productservice.domain.model.ProductAttributeValue;
import jakarta.annotation.PostConstruct;
import org.modelmapper.ModelMapper;
import org.modelmapper.convention.MatchingStrategies;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Component
public class ProductMapper {

    private static final ModelMapper modelMapper = new ModelMapper();

    static {
        modelMapper.getConfiguration()
                .setMatchingStrategy(MatchingStrategies.STRICT)
                .setFieldMatchingEnabled(true)
                .setSkipNullEnabled(true);
    }

    @PostConstruct
    private void configureModelMapper() {

        modelMapper.typeMap(Product.class, ProductDTO.class)
//                .addMapping(src -> src.getImage() != null ? src.getImage().stream().map(productImage -> modelMapper.map(productImage, ProductImageDTO.class))
//                        .collect(Collectors.toList()) : Collections.emptyList(), ProductDTO::setImage)
        ;


        modelMapper.typeMap(ProductDTO.class, Product.class)
                .addMappings(mapper -> {
                    mapper.skip(Product::setCategory);
                    mapper.skip(Product::setSupplier);
                });
//                .addMapping(dest -> dest.getCategory().setName(dest.getCategory()), Product::setCategory)
//                .addMapping(dest -> dest.getSubcategory().setName(dest.getSubcategory()), Product::setSubcategory)
//                .addMapping(dest -> dest.getStore().setName(dest.getStore()), Product::setStore);

    }

    public static Product productDTOToEntity(ProductDTO productDTO) {
        return modelMapper.map(productDTO, Product.class);
    }

    public static ProductDTO mapProductEntityToDTO(Product product) {
        ProductDTO productDTO = modelMapper.map(product, ProductDTO.class);
        productDTO.setImage(mapProductImageListToProductImageDTOList(product.getImage()));
        productDTO.setAttribute(mapProductAttributeValueListToProductAttributeValueDTOList(product.getAttribute()));
        return productDTO;
    }

    private static List<ProductImageDTO> mapProductImageListToProductImageDTOList(Set<ProductImage> productImages) {
        return productImages.stream()
                .map(productImage -> ProductImageMapper.mapProductImageEntityToDTO(productImage))
                .collect(Collectors.toList());
    }

    private static List<ProductAttributeValueDTO> mapProductAttributeValueListToProductAttributeValueDTOList(Set<ProductAttributeValue> productAttributeValues) {
        return productAttributeValues.stream()
                .map(productAttributeValue -> ProductAttributeValueMapper.mapProductAttributeValueEntityToDTO(productAttributeValue))
                .collect(Collectors.toList());
    }

}


