package com.congthanh.productservice.application.service;

import com.congthanh.productservice.application.port.out.CategoryCatalog;
import com.congthanh.productservice.application.port.out.ProductReadModelStore;
import com.congthanh.productservice.application.port.out.product.ProductRepository;
import com.congthanh.productservice.application.port.out.variantOptionCombination.VariantOptionCombinationRepository;
import com.congthanh.productservice.application.readmodel.ProductDocument;
import com.congthanh.productservice.domain.exception.NotFoundException;
import com.congthanh.productservice.domain.model.Product;
import com.congthanh.productservice.domain.model.ProductCategory;
import com.congthanh.productservice.domain.model.ProductImage;
import com.congthanh.productservice.domain.model.VariantOption;
import com.congthanh.productservice.domain.model.VariantOptionCombination;
import com.congthanh.productservice.domain.model.enums.ProductStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.domain.Limit;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.SimpleTransactionStatus;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ProductReadModelServiceTest {

    ProductRepository products = mock(ProductRepository.class);
    ProductReadModelStore store = mock(ProductReadModelStore.class);
    CategoryCatalog catalog = mock(CategoryCatalog.class);
    VariantOptionCombinationRepository combos = mock(VariantOptionCombinationRepository.class);
    PlatformTransactionManager tm = mock(PlatformTransactionManager.class);
    ProductReadModelService service;

    @BeforeEach
    void setUp() {
        when(tm.getTransaction(any())).thenReturn(new SimpleTransactionStatus());
        when(products.snapshotMicros()).thenReturn(1_000L, 2_000L, 3_000L);
        when(store.upsertCore(any())).thenReturn(true);
        service = new ProductReadModelService(products, store, new ProductDocumentAssembler(catalog, combos, "USD"), tm);
    }

    @Test
    void refreshReadsOneRepeatableReadSnapshotAndBuildsFullDocument() {
        Product p = product("p1", "shirt", null);
        p.setThumbnail(11L);
        p.setImage(Set.of(image(10L, "a.jpg", 1, p), image(11L, "b.jpg", 2, p)));
        p.setCategory(new ArrayList<>(List.of(category("c2", 2, p), category("c1", 1, p))));
        when(catalog.byIds(List.of("c1", "c2"))).thenReturn(List.of(
                new CategoryCatalog.CategoryRef("c2", "Áo", "ao"), new CategoryCatalog.CategoryRef("c1", "Nam", "nam")));
        Product red = product("p1-red", "shirt-red", p);
        Product gone = product("p1-old", "shirt-old", p);
        gone.setStatus(ProductStatus.INACTIVE);
        p.setVariant(List.of(red, gone));
        VariantOption color = new VariantOption();
        color.setId(5L);
        color.setName("Màu");
        VariantOptionCombination combo = new VariantOptionCombination();
        combo.setVariantOption(color);
        combo.setValue("Đỏ");
        when(combos.findAllByProduct(red)).thenReturn(List.of(combo));
        when(products.findById("p1")).thenReturn(Optional.of(p));

        service.refresh("p1");

        var tx = ArgumentCaptor.forClass(TransactionDefinition.class);
        verify(tm).getTransaction(tx.capture());
        assertThat(tx.getValue().getIsolationLevel()).isEqualTo(TransactionDefinition.ISOLATION_REPEATABLE_READ);
        assertThat(tx.getValue().isReadOnly()).isTrue();

        var doc = ArgumentCaptor.forClass(ProductDocument.class);
        verify(store).upsertCore(doc.capture());
        ProductDocument d = doc.getValue();
        assertThat(d.getCoreVersion()).isEqualTo(1_000L);
        assertThat(d.getThumbnail()).isEqualTo("b.jpg");
        assertThat(d.getImages()).extracting(ProductDocument.Image::url).containsExactly("a.jpg", "b.jpg");
        assertThat(d.getCategories()).containsExactly(new ProductDocument.Category("c1", "Nam", "nam"),
                new ProductDocument.Category("c2", "Áo", "ao"));
        assertThat(d.getVariants()).singleElement().satisfies(v -> {
            assertThat(v.id()).isEqualTo("p1-red");
            assertThat(v.options()).containsExactly(new ProductDocument.VariantOption(5L, "Màu", "Đỏ"));
        });
        assertThat(d.getCurrency()).isEqualTo("USD");
    }

    @Test
    void refreshingAVariantAlsoRefreshesItsParent() {
        Product parent = product("p1", "shirt", null);
        Product red = product("p1-red", "shirt-red", parent);
        when(products.findById("p1-red")).thenReturn(Optional.of(red));
        when(products.findById("p1")).thenReturn(Optional.of(parent));

        service.refresh("p1-red");

        verify(products).findById("p1-red");
        verify(products).findById("p1");
        verify(store, times(2)).upsertCore(any());
    }

    @Test
    void productGoneFromPostgresIsRemovedFromReadModel() {
        when(products.findById("p9")).thenReturn(Optional.empty());

        service.refresh("p9");

        verify(store).delete("p9");
        verify(store, never()).upsertCore(any());
    }

    @Test
    void readFallsBackToPostgresWithoutWritingWhenDocumentMissing() {
        when(store.findBySlug("shirt")).thenReturn(Optional.empty());
        when(products.findProductBySlug("shirt")).thenReturn(Optional.of(product("p1", "shirt", null)));

        ProductDocument d = service.getBySlug("shirt");

        assertThat(d.getId()).isEqualTo("p1");
        verify(store, never()).upsertCore(any());
    }

    @Test
    void deletedProductIsNotFound() {
        ProductDocument deleted = new ProductDocument();
        deleted.setStatus(ProductStatus.DELETED);
        deleted.setCoreVersion(1L);
        when(store.findBySlug("old")).thenReturn(Optional.of(deleted));

        assertThatThrownBy(() -> service.getBySlug("old")).isInstanceOf(NotFoundException.class);
    }

    @Test
    void rebuildPagesThroughIdsAndCountsFailures() {
        when(products.findIdsAfter(eq(""), any(Limit.class))).thenReturn(List.of("a", "b"));
        when(products.findIdsAfter(eq("b"), any(Limit.class))).thenReturn(List.of());
        when(products.findById("a")).thenReturn(Optional.of(product("a", "a", null)));
        when(products.findById("b")).thenThrow(new IllegalStateException("boom"));

        var result = service.rebuildAll();

        assertThat(result.refreshed()).isEqualTo(1);
        assertThat(result.failed()).isEqualTo(1);
        verify(catalog, never()).byIds(anyList());
    }

    private static Product product(String id, String slug, Product parent) {
        Product p = new Product();
        p.setId(id);
        p.setName(id);
        p.setSlug(slug);
        p.setStatus(ProductStatus.ACTIVE);
        p.setPrice(new BigDecimal("10.00"));
        p.setParent(parent);
        return p;
    }

    private static ProductImage image(Long id, String path, int order, Product p) {
        ProductImage i = new ProductImage();
        i.setId(id);
        i.setImagePath(path);
        i.setDisplayOrder(order);
        i.setProduct(p);
        return i;
    }

    private static ProductCategory category(String id, int order, Product p) {
        ProductCategory c = new ProductCategory();
        c.setCategoryId(id);
        c.setDisplayOrder(order);
        c.setProduct(p);
        return c;
    }
}
