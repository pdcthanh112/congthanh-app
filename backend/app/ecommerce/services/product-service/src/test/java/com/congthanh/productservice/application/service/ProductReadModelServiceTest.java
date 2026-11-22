package com.congthanh.productservice.application.service;

import com.congthanh.commonservice.exception.NotFoundException;
import com.congthanh.productservice.application.port.out.CategoryCatalog;
import com.congthanh.productservice.application.port.out.ProductReadModelStore;
import com.congthanh.productservice.application.port.out.ProductRepository;
import com.congthanh.productservice.application.port.out.ProductSnapshot;
import com.congthanh.productservice.application.port.out.ProductSnapshots;
import com.congthanh.productservice.application.readmodel.ProductDocument;
import com.congthanh.productservice.domain.model.Product;
import com.congthanh.productservice.domain.model.enums.ProductStatus;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.SimpleTransactionStatus;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ProductReadModelServiceTest {

    final FakeSnapshots snapshots = new FakeSnapshots();
    final FakeStore store = new FakeStore();
    final FakeCatalog catalog = new FakeCatalog();
    final FakeTx tx = new FakeTx();
    final ProductReadModelService service = new ProductReadModelService(snapshots, new FakeIds(snapshots), store,
            new ProductDocumentAssembler(catalog, Clock.fixed(Instant.parse("2026-11-22T00:00:00Z"), ZoneOffset.UTC)), tx);

    @Test
    void refreshReadsOneRepeatableReadSnapshotAndBuildsFullDocument() {
        var red = snapshot("p1-red", null, "p1", ProductStatus.ACTIVE, List.of(),
                List.of(new ProductSnapshot.Option(5L, "Màu", 7L, "Đỏ", 1, 1)), List.of());
        var gone = snapshot("p1-old", null, "p1", ProductStatus.INACTIVE, List.of(),
                List.of(new ProductSnapshot.Option(5L, "Màu", 8L, "Xanh", 2, 1)), List.of());
        snapshots.put(snapshot("p1", "shirt", null, ProductStatus.ACTIVE,
                List.of(new ProductSnapshot.Image(11L, "b.jpg", 2), new ProductSnapshot.Image(10L, "a.jpg", 1)),
                List.of(), List.of(red, gone)), 11L, List.of("c1", "c2"));
        catalog.categories.put("c1", new CategoryCatalog.CategoryRef("c1", "Nam", "nam"));
        catalog.categories.put("c2", new CategoryCatalog.CategoryRef("c2", "Áo", "ao"));

        service.refresh("p1");

        assertThat(tx.definitions).singleElement().satisfies(d -> {
            assertThat(d.getIsolationLevel()).isEqualTo(TransactionDefinition.ISOLATION_REPEATABLE_READ);
            assertThat(d.isReadOnly()).isTrue();
        });
        ProductDocument d = store.upserts.getFirst();
        assertThat(d.getCoreVersion()).isEqualTo(1_000L);
        assertThat(d.getThumbnail()).isEqualTo("b.jpg");
        assertThat(d.getImages()).extracting(ProductDocument.Image::url).containsExactly("a.jpg", "b.jpg");
        assertThat(d.getCategories()).containsExactly(new ProductDocument.Category("c1", "Nam", "nam"),
                new ProductDocument.Category("c2", "Áo", "ao"));
        // Chỉ variant đang bán; bộ chọn option chỉ gồm giá trị của variant đang bán.
        assertThat(d.getVariants()).singleElement().satisfies(v -> {
            assertThat(v.id()).isEqualTo("p1-red");
            assertThat(v.options()).containsExactly(new ProductDocument.VariantOption(5L, "Màu", 7L, "Đỏ"));
        });
        assertThat(d.getOptionGroups()).containsExactly(new ProductDocument.OptionGroup(5L, "Màu",
                List.of(new ProductDocument.OptionChoice(7L, "Đỏ", 1))));
        assertThat(d.getCurrency()).isEqualTo("USD");
    }

    @Test
    void refreshingAVariantAlsoRefreshesItsParent() {
        snapshots.put(snapshot("p1", "shirt", null, ProductStatus.ACTIVE, List.of(), List.of(), List.of()));
        snapshots.put(snapshot("p1-red", null, "p1", ProductStatus.ACTIVE, List.of(), List.of(), List.of()));

        service.refresh("p1-red");

        assertThat(store.upserts).extracting(ProductDocument::getId).containsExactly("p1-red", "p1");
    }

    @Test
    void productGoneFromPostgresIsRemovedFromReadModel() {
        service.refresh("p9");

        assertThat(store.deleted).containsExactly("p9");
        assertThat(store.upserts).isEmpty();
    }

    @Test
    void readFallsBackToPostgresWithoutWritingWhenDocumentMissing() {
        snapshots.put(snapshot("p1", "shirt", null, ProductStatus.ACTIVE, List.of(), List.of(), List.of()));

        ProductDocument d = service.getBySlug("shirt");

        assertThat(d.getId()).isEqualTo("p1");
        assertThat(store.upserts).isEmpty();
    }

    @Test
    void deletedProductIsNotFound() {
        ProductDocument deleted = new ProductDocument();
        deleted.setStatus(ProductStatus.DELETED);
        deleted.setCoreVersion(1L);
        store.bySlug.put("old", deleted);

        assertThatThrownBy(() -> service.getBySlug("old")).isInstanceOf(NotFoundException.class);
    }

    @Test
    void rebuildPagesThroughIdsAndCountsFailures() {
        snapshots.put(snapshot("a", "a", null, ProductStatus.ACTIVE, List.of(), List.of(), List.of()));
        snapshots.failing.add("b");

        var result = service.rebuildAll();

        assertThat(result.refreshed()).isEqualTo(1);
        assertThat(result.failed()).isEqualTo(1);
        assertThat(catalog.calls).isZero();
    }

    private static ProductSnapshot snapshot(String id, String slug, String parentId, ProductStatus status,
                                            List<ProductSnapshot.Image> images, List<ProductSnapshot.Option> options,
                                            List<ProductSnapshot> variants) {
        return new ProductSnapshot(id, parentId, id, slug, null, null, null, status, false, !variants.isEmpty(), null, null,
                new BigDecimal("10.00"), "USD", null, images, List.of(), List.of(), options, variants);
    }

    /** Bản chụp đọc từ "Postgres" giả; id được ghi theo thứ tự thêm để rebuild quét theo lô. */
    static final class FakeSnapshots implements ProductSnapshots {
        final Map<String, ProductSnapshot> byId = new HashMap<>();
        final List<String> ids = new ArrayList<>();
        final List<String> failing = new ArrayList<>();
        private long clock = 0;

        void put(ProductSnapshot s) {
            byId.put(s.id(), s);
            ids.add(s.id());
        }

        void put(ProductSnapshot s, Long thumbnailId, List<String> categoryIds) {
            put(new ProductSnapshot(s.id(), s.parentId(), s.name(), s.slug(), s.sku(), s.gtin(), s.description(), s.status(),
                    s.featured(), s.hasVariant(), s.supplier(), s.brandId(), s.price(), s.currency(), thumbnailId, s.images(),
                    categoryIds, s.attributes(), s.options(), s.variants()));
        }

        @Override
        public long snapshotMicros() {
            clock += 1_000L;
            return clock;
        }

        @Override
        public Optional<ProductSnapshot> byId(String id) {
            if (failing.contains(id)) {
                throw new IllegalStateException("boom");
            }
            return Optional.ofNullable(byId.get(id));
        }

        @Override
        public Optional<ProductSnapshot> bySlug(String slug) {
            return byId.values().stream().filter(s -> slug.equals(s.slug())).findFirst();
        }
    }

    /** Chỉ phần quét id của ProductRepository được read model dùng. */
    static final class FakeIds implements ProductRepository {
        private final FakeSnapshots snapshots;

        FakeIds(FakeSnapshots snapshots) {
            this.snapshots = snapshots;
        }

        @Override
        public List<String> idsAfter(String after, int limit) {
            List<String> all = new ArrayList<>(snapshots.ids);
            all.addAll(snapshots.failing);
            int from = "0".equals(after) ? 0 : all.indexOf(after) + 1;
            return all.subList(from, Math.min(all.size(), from + limit));
        }

        @Override
        public Optional<Product> findById(String id) {
            throw new UnsupportedOperationException();
        }

        @Override
        public List<Product> findVariants(String parentId) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void save(Product product) {
            throw new UnsupportedOperationException();
        }

        @Override
        public boolean existsSkuOnOther(String sku, String productId) {
            throw new UnsupportedOperationException();
        }

        @Override
        public boolean existsSlugOnOther(String slug, String productId) {
            throw new UnsupportedOperationException();
        }

        @Override
        public boolean existsGtinOnOther(String gtin, String productId) {
            throw new UnsupportedOperationException();
        }

        @Override
        public boolean existsLiveVariantKey(String parentId, String variantKey) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void republish(String productId) {
            throw new UnsupportedOperationException();
        }
    }

    static final class FakeStore implements ProductReadModelStore {
        final List<ProductDocument> upserts = new ArrayList<>();
        final List<String> deleted = new ArrayList<>();
        final Map<String, ProductDocument> bySlug = new HashMap<>();

        @Override
        public Optional<ProductDocument> findById(String id) {
            return Optional.empty();
        }

        @Override
        public Optional<ProductDocument> findBySlug(String slug) {
            return Optional.ofNullable(bySlug.get(slug));
        }

        @Override
        public boolean upsertCore(ProductDocument core) {
            upserts.add(core);
            return true;
        }

        @Override
        public void delete(String id) {
            deleted.add(id);
        }

        @Override
        public void applyPrice(String productId, String priceList, BigDecimal amount, String currency, Instant at) {
        }

        @Override
        public void applyRating(String productId, double average, long count, List<Long> stars, Instant at) {
        }

        @Override
        public void applyStock(String productId, String warehouseId, int available, Instant at) {
        }

        @Override
        public void applyCategory(String categoryId, String name, String slug, Instant at) {
        }

        @Override
        public void applyBrand(long brandId, String name, String slug, Instant at) {
        }
    }

    static final class FakeCatalog implements CategoryCatalog {
        final Map<String, CategoryRef> categories = new HashMap<>();
        int calls;

        @Override
        public CategoryRef byId(String categoryId) {
            return categories.get(categoryId);
        }

        @Override
        public CategoryRef bySlug(String slug) {
            throw new UnsupportedOperationException();
        }

        @Override
        public List<CategoryRef> byIds(List<String> categoryIds) {
            calls++;
            return categoryIds.stream().map(categories::get).filter(c -> c != null).toList();
        }

        @Override
        public void validate(List<String> categoryIds) {
        }
    }

    /** Ghi lại định nghĩa transaction để kiểm REPEATABLE READ + read-only. */
    static final class FakeTx implements PlatformTransactionManager {
        final List<TransactionDefinition> definitions = new ArrayList<>();

        @Override
        public TransactionStatus getTransaction(TransactionDefinition definition) {
            definitions.add(definition);
            return new SimpleTransactionStatus();
        }

        @Override
        public void commit(TransactionStatus status) {
        }

        @Override
        public void rollback(TransactionStatus status) {
        }
    }
}
