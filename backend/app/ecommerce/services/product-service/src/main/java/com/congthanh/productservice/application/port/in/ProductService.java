package com.congthanh.productservice.application.port.in;

import com.congthanh.productservice.application.dto.ProductDTO;
import com.congthanh.productservice.application.dto.request.CreateProductRequest;
import com.congthanh.productservice.domain.model.Product;
import com.congthanh.productservice.application.dto.response.ResponseWithPagination;
import com.congthanh.productservice.application.dto.viewmodel.ProductDetailVm;
import com.congthanh.productservice.application.dto.viewmodel.ProductVariantVm;
import com.congthanh.productservice.application.dto.viewmodel.ProductVm;

import java.util.List;

public interface ProductService {

    ResponseWithPagination<ProductDTO> getAllProduct(Integer page, Integer limit);

    ProductDTO getProductById(String id);

    ProductDTO getProductDetailById(String id);

    ProductDTO getProductDTOBySlug(String slug);

    ProductVm getProductVmBySlug(String slug);

    ProductDetailVm getProductDetailBySlug(String slug);

    ProductDTO createProduct(CreateProductRequest request);

    Product updateProduct(ProductDTO productDTO);

    boolean deleteProduct(String id);

    List<ProductVariantVm> getProductVariationsByParentId(String parentId);

    ResponseWithPagination<ProductDTO> getProductByCategoryId(String categoryId, int page, int limit);

    ResponseWithPagination<ProductDTO> getProductByCategorySlug(String categoryId, int page, int limit);

    List<ProductDTO> searchProduct(String keyword);

    Long getSoldByProduct(String productId);

    List<?> getListFeaturedProducts(int page, int limit);

//  List<ProductVariantAttributeValueDTO> getVariantAttributeValueByProduct(String productId);

    ResponseWithPagination<ProductDTO> getProductWithFilter(int page, int limit, String keyword, String category, String brand, int rating, Double startPrice, Double endPrice);

}
