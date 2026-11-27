package com.congthanh.productservice.adapter.out.persistence;

import com.congthanh.commonservice.exception.ConflictException;
import com.congthanh.productservice.domain.model.Specifications;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

import java.util.List;

/**
 * Cột {@code products.specifications} (jsonb, ADR 0033) đọc/ghi bằng JdbcClient thay vì qua entity JPA: JSON do domain
 * kiểm cấu trúc, DB kiểm là mảng và giới hạn kích thước.
 */
@Component
class ProductSpecificationsStore {

    private static final JsonMapper JSON = JsonMapper.builder().build();
    private static final TypeReference<List<Specifications.Group>> GROUPS = new TypeReference<>() {
    };

    private final JdbcClient jdbc;

    ProductSpecificationsStore(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    /** Thông số của sản phẩm; không có sản phẩm hoặc chưa có thông số thì rỗng. */
    Specifications load(String productId) {
        return jdbc.sql("SELECT specifications::text FROM products WHERE id = :id")
                .param("id", Long.parseLong(productId))
                .query(String.class)
                .optional()
                .map(ProductSpecificationsStore::parse)
                .orElse(Specifications.EMPTY);
    }

    /**
     * Ghi thông số và tăng version như mọi thay đổi khác của sản phẩm; version đã đổi (người khác vừa sửa) thì 409.
     * Gọi sau khi entity đã flush, trong cùng transaction.
     */
    @Transactional(propagation = Propagation.MANDATORY)
    void save(String productId, Specifications specifications, long expectedVersion) {
        int updated = jdbc.sql("""
                        UPDATE products SET specifications = CAST(:json AS jsonb), version = version + 1
                        WHERE id = :id AND version = :version""")
                .param("json", JSON.writeValueAsString(specifications.groups()))
                .param("id", Long.parseLong(productId))
                .param("version", expectedVersion)
                .update();
        if (updated == 0) {
            throw new ConflictException("product was changed by someone else");
        }
    }

    static Specifications parse(String json) {
        if (json == null || json.isBlank()) {
            return Specifications.EMPTY;
        }
        return new Specifications(JSON.readValue(json, GROUPS));
    }
}
