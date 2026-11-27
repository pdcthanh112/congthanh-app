package com.congthanh.productservice.application.service;

import com.congthanh.commonservice.exception.NotFoundException;
import com.congthanh.commonservice.exception.PermissionException;
import com.congthanh.productservice.application.dto.Actor;
import com.congthanh.productservice.application.port.out.ProductRepository;
import com.congthanh.productservice.domain.model.Product;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ProductAccessServiceTest {

    private final FakeProducts products = new FakeProducts();
    private final ProductAccessService access = new ProductAccessService(products);

    @Test
    void sellerModifiesOnlyOwnProducts() {
        products.add(product("p1", "seller-1"));

        assertThatCode(() -> access.requireModifiable("p1", new Actor("seller-1", false))).doesNotThrowAnyException();
        assertThatThrownBy(() -> access.requireModifiable("p1", new Actor("seller-2", false)))
                .isInstanceOf(PermissionException.class);
    }

    @Test
    void platformProductsAreAdminOnly() {
        products.add(product("p2", null));

        assertThatThrownBy(() -> access.requireModifiable("p2", new Actor("seller-1", false)))
                .isInstanceOf(PermissionException.class);
        assertThatCode(() -> access.requireModifiable("p2", new Actor("admin-1", true))).doesNotThrowAnyException();
    }

    @Test
    void missingProductIsNotFound() {
        assertThatThrownBy(() -> access.requireModifiable("nope", new Actor("admin-1", true)))
                .isInstanceOf(NotFoundException.class);
    }

    @Test
    void sellerOwnsWhatTheyCreateAndAdminCreatesPlatformProducts() {
        assertThat(new Actor("seller-1", false).ownerOfNewProduct()).isEqualTo("seller-1");
        assertThat(new Actor("admin-1", true).ownerOfNewProduct()).isNull();
    }

    private static Product product(String id, String ownerId) {
        return Product.create(new Product.Draft(id, "Shirt", "shirt-" + id, null, null, null, null, null, null, false,
                List.of("c1"), List.of(), null, ownerId));
    }

    private static final class FakeProducts implements ProductRepository {
        private final Map<String, Product> byId = new HashMap<>();

        void add(Product p) {
            byId.put(p.id(), p);
        }

        @Override public Optional<Product> findById(String id) { return Optional.ofNullable(byId.get(id)); }
        @Override public List<Product> findVariants(String parentId) { return List.of(); }
        @Override public void save(Product product) { byId.put(product.id(), product); }
        @Override public boolean existsSkuOnOther(String sku, String productId) { return false; }
        @Override public boolean existsSlugOnOther(String slug, String productId) { return false; }
        @Override public boolean existsGtinOnOther(String gtin, String productId) { return false; }
        @Override public boolean existsLiveVariantKey(String parentId, String variantKey) { return false; }
        @Override public void republish(String productId) { }
        @Override public List<String> idsAfter(String after, int limit) { return List.of(); }
    }
}
