package com.congthanh.productservice.application.port.in;

import com.congthanh.productservice.application.dto.ProductDTO;
import com.congthanh.productservice.application.dto.request.ChangeSpecificationsRequest;
import com.congthanh.productservice.application.dto.request.CreateProductRequest;
import com.congthanh.productservice.application.dto.request.UpdateProductRequest;
import com.congthanh.productservice.application.dto.response.ResponseWithPagination;
import com.congthanh.productservice.application.dto.response.VariantValueResponse;
import com.congthanh.productservice.application.dto.viewmodel.ProductDetailVm;
import com.congthanh.productservice.application.dto.viewmodel.ProductVariantVm;
import com.congthanh.productservice.application.dto.viewmodel.ProductVm;
import com.congthanh.productservice.domain.model.Product;
import com.congthanh.productservice.domain.model.enums.ProductStatus;

import java.util.Collection;
import java.util.List;

public interface ProductService {

    /** Sản phẩm gốc đang bán, trang đánh số từ 1. */
    ResponseWithPagination<ProductDTO> getAllProduct(Integer page, Integer limit);

    ProductDTO getProductById(String id);

    ProductDTO getProductDetailById(String id);

    ProductDTO getProductDTOBySlug(String slug);

    ProductVm getProductVmBySlug(String slug);

    ProductDetailVm getProductDetailBySlug(String slug);

    /**
     * Tạo sản phẩm gốc kèm danh mục, ảnh, thuộc tính, variant trong một transaction; trùng slug/SKU/GTIN thì 409.
     * {@code ownerId}: subject của SELLER sở hữu, {@code null} là sản phẩm của sàn.
     */
    ProductDTO createProduct(CreateProductRequest request, String ownerId);

    /** Sửa từng phần; version lệch thì 409. */
    ProductDTO updateProduct(UpdateProductRequest request);

    /** Thay toàn bộ thông số tự do của sản phẩm gốc (ADR 0033); version cũ thì 409, sản phẩm là biến thể thì 400. */
    ProductDTO changeSpecifications(String id, ChangeSpecificationsRequest request);

    /** Bật/tắt bán. Bật bán cần có giá (hoặc có variant đang bán có giá). Không dùng để xoá. */
    ProductDTO changeStatus(String id, ProductStatus status);

    /** Xoá mềm; xoá sản phẩm gốc thì xoá luôn các variant. */
    void deleteProduct(String id);

    List<ProductVariantVm> getProductVariationsByParentId(String parentId);

    /** Các trục variant và giá trị đang bán của sản phẩm gốc (đọc từ read model). */
    List<VariantValueResponse> getVariantOptionGroups(String productId);

    ResponseWithPagination<ProductDTO> getProductByCategoryId(String categoryId, int page, int limit);

    ResponseWithPagination<ProductDTO> getProductByCategorySlug(String slug, int page, int limit);

    ResponseWithPagination<ProductDTO> getFeaturedProducts(int page, int limit);

    /** Tra cứu theo lô cho gRPC (giữ nguyên sản phẩm đã xoá; người gọi tự lọc). */
    List<Product> findByIds(Collection<String> ids);

    /** Tra cứu theo tên/SKU cho back-office; storefront tìm kiếm qua search-service. */
    List<ProductDTO> searchProduct(String keyword);
}
