package com.congthanh.productservice.application.readmodel;

import com.congthanh.productservice.domain.model.enums.ProductStatus;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Data
@NoArgsConstructor
@Document(collection = "product")
public class ProductDocument {

    @Id
    private String id;
    private String slug;
    private String name;
    private String sku;
    private String gtin;
    private String description;
    private ProductStatus status;
    private boolean featured;
    private boolean hasVariant;
    private String parentId;
    private String supplier;
    private Brand brand;
    private List<Category> categories = new ArrayList<>();
    private String thumbnail;
    private List<Image> images = new ArrayList<>();
    private List<Attribute> attributes = new ArrayList<>();
    private List<Variant> variants = new ArrayList<>();
    private BigDecimal basePrice;
    private String currency;

    private Map<String, Price> prices = new HashMap<>();
    private Rating rating;
    private Map<String, Integer> stock = new HashMap<>();
    private Boolean inStock;

    private Long coreVersion;
    /** Mốc thời gian (epoch ms) của lần cập nhật từng phần: "price_<list>", "rating", "stock_<kho>", ... */
    private Map<String, Long> versions = new HashMap<>();
    private Instant updatedAt;

    public record Brand(Long id, String name, String slug) {
    }

    public record Category(String id, String name, String slug) {
    }

    public record Image(Long id, String url, int order) {
    }

    public record Attribute(String name, String value) {
    }

    public record VariantOption(Long optionId, String name, String value) {
    }

    public record Variant(String id, String name, String slug, String sku, String gtin, BigDecimal price,
                          String thumbnail, List<Image> images, List<VariantOption> options) {
    }

    public record Price(BigDecimal amount, String currency, Instant effectiveAt) {
    }

    public record Rating(double average, long count, List<Long> stars) {
    }

    public boolean isComplete() {
        return coreVersion != null;
    }

    /** Giá bán theo bảng giá {@code priceList}; chưa có thì dùng giá gốc. */
    public BigDecimal effectivePrice(String priceList) {
        Price p = prices == null ? null : prices.get(priceList);
        return p != null ? p.amount() : basePrice;
    }
}
