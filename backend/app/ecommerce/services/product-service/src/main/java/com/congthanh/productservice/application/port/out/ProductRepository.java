package com.congthanh.productservice.application.port.out;

import com.congthanh.productservice.domain.model.Product;

import java.util.List;
import java.util.Optional;

/** Lưu/đọc aggregate Product. Lưu là ghi luôn event của aggregate vào outbox trong cùng transaction. */
public interface ProductRepository {

    /** Kể cả sản phẩm đã xoá mềm (aggregate tự chặn thao tác trên sản phẩm đã xoá). */
    Optional<Product> findById(String id);

    /** Các variant của sản phẩm gốc, kể cả đã xoá mềm. */
    List<Product> findVariants(String parentId);

    /** Cần transaction đang mở. Version lệch (người khác vừa sửa) thì 409. */
    void save(Product product);

    /** SKU (không phân biệt hoa thường) đã thuộc sản phẩm khác, kể cả đã xoá. {@code productId} null khi tạo mới. */
    boolean existsSkuOnOther(String sku, String productId);

    /** Slug đã thuộc sản phẩm khác chưa xoá. */
    boolean existsSlugOnOther(String slug, String productId);

    /** GTIN đã thuộc sản phẩm khác chưa xoá. */
    boolean existsGtinOnOther(String gtin, String productId);

    /** Đã có variant chưa xoá của {@code parentId} mang tổ hợp {@code variantKey}. */
    boolean existsLiveVariantKey(String parentId, String variantKey);

    /** Phát lại bản chụp hiện tại của sản phẩm vào outbox (dựng lại chỉ mục ở consumer). Cần transaction đang mở. */
    void republish(String productId);

    /** Id theo thứ tự tăng, sau {@code after}; dùng cho quét toàn bộ theo lô. */
    List<String> idsAfter(String after, int limit);
}
