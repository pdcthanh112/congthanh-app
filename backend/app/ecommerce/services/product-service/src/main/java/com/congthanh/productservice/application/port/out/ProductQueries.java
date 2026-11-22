package com.congthanh.productservice.application.port.out;

import com.congthanh.productservice.application.dto.ProductAttributeValueDTO;
import com.congthanh.productservice.application.dto.ProductDTO;
import com.congthanh.productservice.application.dto.ProductImageDTO;
import com.congthanh.productservice.application.dto.response.ResponseWithPagination;
import com.congthanh.productservice.application.dto.viewmodel.ProductOptionCombinationGetVm;
import com.congthanh.productservice.application.dto.viewmodel.ProductOptionValueGetVm;
import com.congthanh.productservice.application.dto.viewmodel.ProductVariantVm;

import java.util.List;
import java.util.Optional;

/** Truy vấn đọc thẳng từ write model ra DTO (back-office, danh sách); không đi qua aggregate. Trang đánh số từ 1. */
public interface ProductQueries {

    /** Sản phẩm gốc đang bán. */
    ResponseWithPagination<ProductDTO> active(int page, int limit);

    ResponseWithPagination<ProductDTO> activeInCategory(String categoryId, int page, int limit);

    ResponseWithPagination<ProductDTO> featured(int page, int limit);

    /** Sản phẩm (kể cả variant, kể cả đã xoá) kèm danh mục, ảnh, thuộc tính. */
    Optional<ProductDTO> detail(String id);

    /** Sản phẩm gốc chưa xoá có tên chứa từ khoá hoặc đúng SKU; khớp SKU trước. */
    List<ProductDTO> lookup(String keyword, int limit);

    /** Variant đang bán của sản phẩm gốc, kèm ảnh và giá trị option. */
    List<ProductVariantVm> activeVariants(String parentId);

    /** Giá trị trục khai báo trên sản phẩm gốc. */
    List<ProductOptionValueGetVm> optionValues(String parentId);

    /** Giá trị trục đang được ít nhất một variant chưa xoá dùng (mỗi giá trị một lần). */
    List<ProductOptionCombinationGetVm> usedOptionValues(String parentId);

    List<ProductAttributeValueDTO> attributeValues(String productId);

    /** Ảnh mặc định trước, rồi theo thứ tự hiển thị. */
    List<ProductImageDTO> images(String productId);
}
