package com.congthanh.productservice.adapter.out.persistence;

import com.congthanh.commonservice.exception.BadRequestException;
import com.congthanh.commonservice.exception.ConflictException;
import com.congthanh.platform.outbox.OutboxWriter;
import com.congthanh.productservice.application.dto.ProductCategoryDTO;
import com.congthanh.productservice.application.dto.ProductDTO;
import com.congthanh.productservice.application.dto.request.CreateProductRequest;
import com.congthanh.productservice.application.dto.request.CreateVariantRequest;
import com.congthanh.productservice.application.dto.request.CreateVariantRequest.OptionSelection;
import com.congthanh.productservice.application.dto.request.UpdateProductRequest;
import com.congthanh.productservice.application.port.in.ProductReadModelUseCase;
import com.congthanh.productservice.application.port.out.CategoryCatalog;
import com.congthanh.productservice.application.service.ProductCategoryServiceImpl;
import com.congthanh.productservice.application.service.ProductImageServiceImpl;
import com.congthanh.productservice.application.service.ProductServiceImpl;
import com.congthanh.productservice.application.service.VariantOptionServiceImpl;
import com.congthanh.productservice.domain.model.enums.ProductStatus;
import com.congthanh.productservice.grpc.ProductUpserted;
import com.google.protobuf.Any;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.aot.DisabledInAotMode;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

import java.lang.reflect.Proxy;
import java.math.BigDecimal;
import java.time.Clock;
import java.util.List;
import javax.sql.DataSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Changelog thật (Liquibase + seed) trên Postgres 18: Hibernate validate schema, adapter persistence lưu aggregate và
 * ghi outbox, use case tạo/sửa/xoá sản phẩm và variant, ràng buộc DB (ADR 0030). Cần Docker:
 * {@code ./gradlew :services:product-service:integrationTest}.
 */
@Tag("integration")
@DisabledInAotMode // cần container lúc chạy, không dựng context sẵn lúc build AOT
@Testcontainers
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@TestPropertySource(properties = {
        "spring.jpa.hibernate.ddl-auto=validate",
        "spring.liquibase.contexts=seed"
})
@Import({ProductPersistenceAdapter.class, ProductSnapshotReader.class, ProductQueryAdapter.class,
        ProductAttributePersistenceAdapter.class, VariantOptionPersistenceAdapter.class,
        ProductServiceImpl.class, VariantOptionServiceImpl.class, ProductCategoryServiceImpl.class, ProductImageServiceImpl.class,
        CatalogSchemaIntegrationTest.Fakes.class})
class CatalogSchemaIntegrationTest {

    static final String IPHONE = "369334726608713";
    static final long COLOR = 1865809232457271L;
    static final long CAPACITY = 1865809232457272L;
    static final long PAGES = 16556939800219645L;

    @Container
    static PostgreSQLContainer postgres = new PostgreSQLContainer(
            DockerImageName.parse("pgvector/pgvector:0.8.1-pg18-trixie").asCompatibleSubstituteFor("postgres"))
            .withInitScript("db/integration-init.sql");

    @DynamicPropertySource
    static void datasource(DynamicPropertyRegistry r) {
        r.add("spring.datasource.url", () -> postgres.getJdbcUrl() + "&currentSchema=product_service");
        r.add("spring.datasource.username", postgres::getUsername);
        r.add("spring.datasource.password", postgres::getPassword);
    }

    /** Cổng ra không thuộc DB: danh mục (catalog-service) và read model chỉ cần bản giả tối thiểu; outbox thật. */
    @TestConfiguration
    static class Fakes {
        @Bean
        OutboxWriter outboxWriter(DataSource dataSource) {
            return new OutboxWriter(JdbcClient.create(dataSource), "product-service", Clock.systemUTC());
        }

        @Bean
        CategoryCatalog categoryCatalog() {
            return new CategoryCatalog() {
                @Override
                public CategoryRef byId(String id) {
                    return new CategoryRef(id, id, id);
                }

                @Override
                public CategoryRef bySlug(String slug) {
                    return new CategoryRef(slug, slug, slug);
                }

                @Override
                public List<CategoryRef> byIds(List<String> ids) {
                    return ids.stream().map(this::byId).toList();
                }

                @Override
                public void validate(List<String> ids) {
                    if (ids.contains("missing")) {
                        throw new IllegalArgumentException("unknown category missing");
                    }
                }
            };
        }

        @Bean
        ProductReadModelUseCase readModel() {
            return (ProductReadModelUseCase) Proxy.newProxyInstance(getClass().getClassLoader(),
                    new Class<?>[]{ProductReadModelUseCase.class}, (proxy, m, args) -> {
                        throw new UnsupportedOperationException(m.getName());
                    });
        }
    }

    @Autowired ProductJpaRepository products;
    @Autowired VariantOptionCombinationJpaRepository combinations;
    @Autowired ProductQueryAdapter queries;
    @Autowired ProductServiceImpl productService;
    @Autowired VariantOptionServiceImpl variants;
    @Autowired ProductCategoryServiceImpl categoryService;
    @Autowired ProductImageServiceImpl imageService;
    @Autowired JdbcTemplate jdbc;

    @Test
    void seedIsNormalisedAndQueriesWork() {
        assertThat(products.findVariants(IPHONE)).hasSize(12).allSatisfy(v -> assertThat(v.getVariantKey()).isNotBlank());
        assertThat(combinations.findByParent(IPHONE)).hasSize(24);
        assertThat(combinations.findByVariant("772266622382519"))
                .extracting(c -> c.getVariantOption().getName() + "=" + c.getVariantOptionValue().getValue())
                .containsExactly("Color=Black", "Capacity=128 GB");
        assertThat(queries.lookup("iphone 16", 50)).extracting(ProductDTO::getSlug).contains("iphone-16-pro-max", "iphone-16-pro");
        assertThat(queries.activeInCategory("3247917155611621", 1, 10).getResponseList()).extracting(ProductDTO::getId)
                .containsExactly(IPHONE);
        assertThat(queries.detail("279094089499572")).get().satisfies(p -> assertThat(p.getAttribute()).hasSize(4));
        assertThat(queries.usedOptionValues(IPHONE)).hasSize(7);
    }

    @Test
    void createVariantReusesValuesComputesKeyAndRejectsDuplicates() {
        var created = variants.createVariant(IPHONE, new CreateVariantRequest(null, "IP16PM-BLUE-128", null,
                new BigDecimal("27000000"), "vnd", null,
                List.of(new OptionSelection(COLOR, "Blue"), new OptionSelection(CAPACITY, "128 gb"))));
        products.flush();

        ProductEntity v = products.findById(created.id()).orElseThrow();
        assertThat(v.getParent().getId()).isEqualTo(IPHONE);
        assertThat(v.getCurrency()).isEqualTo("VND");
        assertThat(v.getName()).isEqualTo("Iphone 16 Pro Max Blue 128 GB");
        // "128 gb" khớp giá trị đã có (không phân biệt hoa thường), "Blue" được khai báo thêm.
        assertThat(v.getVariantKey()).startsWith("1965809232457273-");
        assertThat(created.options()).containsEntry(COLOR, "Blue").containsEntry(CAPACITY, "128 GB");
        // Outbox: bản chụp variant và bản chụp sản phẩm gốc (có variant mới).
        ProductUpserted parent = lastUpserted(IPHONE);
        assertThat(parent.getVariantsList()).hasSize(13);
        assertThat(lastUpserted(created.id()).getParentId()).isEqualTo(IPHONE);

        assertThatThrownBy(() -> variants.createVariant(IPHONE, new CreateVariantRequest(null, "IP16PM-BLUE-128-B", null,
                null, null, null, List.of(new OptionSelection(CAPACITY, "128 GB"), new OptionSelection(COLOR, "blue")))))
                .isInstanceOf(ConflictException.class);
        assertThatThrownBy(() -> variants.createVariant(IPHONE, new CreateVariantRequest(null, "ip16pm-blue-128", null,
                null, null, null, List.of(new OptionSelection(COLOR, "Red"), new OptionSelection(CAPACITY, "2 TB")))))
                .isInstanceOf(ConflictException.class).hasMessageContaining("sku");
        assertThatThrownBy(() -> variants.createVariant(IPHONE, new CreateVariantRequest(null, "IP16PM-RED", null,
                null, null, null, List.of(new OptionSelection(COLOR, "Red")))))
                .isInstanceOf(BadRequestException.class).hasMessageContaining("same options");
    }

    @Test
    void createProductWithImagesAttributesThenUpdateAndDelete() {
        var created = productService.createProduct(new CreateProductRequest("Đồng hồ Test", null, "WATCH-1", "12345678",
                new BigDecimal("1000"), "usd", "mô tả", null, 7L, true, null, List.of("c1", "c2"),
                List.of(new CreateProductRequest.ImageRequest(900L, "https://img/a.jpg", 1),
                        new CreateProductRequest.ImageRequest(901L, "https://img/b.jpg", 2)),
                901L, List.of(new CreateProductRequest.AttributeRequest(PAGES, "320")), List.of()));

        assertThat(created.getSlug()).isEqualTo("dong-ho-test");
        assertThat(created.getStatus()).isEqualTo(ProductStatus.ACTIVE);
        assertThat(created.getCurrency()).isEqualTo("USD");
        assertThat(created.getThumbnail()).isEqualTo("https://img/b.jpg");
        assertThat(created.getCategory()).extracting(ProductCategoryDTO::getCategoryId).containsExactly("c1", "c2");
        assertThat(created.getAttribute()).singleElement().satisfies(a -> assertThat(a.getValueNumber()).isEqualByComparingTo("320"));
        assertThat(lastUpserted(created.getId()).getStatus()).isEqualTo("ACTIVE");

        assertThatThrownBy(() -> productService.createProduct(minimal("x", null, "watch-1", List.of("c1"))))
                .isInstanceOf(ConflictException.class).hasMessageContaining("sku");
        assertThatThrownBy(() -> productService.createProduct(minimal("y", "dong-ho-test", null, List.of("c1"))))
                .isInstanceOf(ConflictException.class).hasMessageContaining("slug");
        assertThatThrownBy(() -> productService.createProduct(minimal("z", null, null, List.of("missing"))))
                .isInstanceOf(BadRequestException.class);
        assertThat(productService.createProduct(minimal("Đồng hồ Test", null, null, List.of("c1"))).getSlug())
                .startsWith("dong-ho-test-");

        var updated = productService.updateProduct(new UpdateProductRequest(created.getId(), created.getVersion(), "Đồng hồ Mới",
                null, null, null, new BigDecimal("1200"), null, null, null, null, false, 900L, List.of("c2", "c3")));
        assertThat(updated.getName()).isEqualTo("Đồng hồ Mới");
        assertThat(updated.getPrice()).isEqualByComparingTo("1200");
        assertThat(updated.getCurrency()).isEqualTo("USD");
        assertThat(updated.getThumbnail()).isEqualTo("https://img/a.jpg");
        assertThat(updated.getCategory()).extracting(ProductCategoryDTO::getCategoryId).containsExactly("c2", "c3");
        assertThatThrownBy(() -> productService.updateProduct(new UpdateProductRequest(created.getId(), created.getVersion(),
                "cũ", null, null, null, null, null, null, null, null, null, null, null)))
                .isInstanceOf(ConflictException.class).hasMessageContaining("version");

        categoryService.addCategoryToProduct(created.getId(), "c4");
        assertThatThrownBy(() -> categoryService.addCategoryToProduct(created.getId(), "c4")).isInstanceOf(ConflictException.class);
        imageService.deleteProductImage(created.getId(), 900L);
        assertThat(products.findById(created.getId()).orElseThrow().getThumbnail()).isEqualTo(901L);

        assertThat(productService.changeStatus(created.getId(), ProductStatus.INACTIVE).getStatus()).isEqualTo(ProductStatus.INACTIVE);
        productService.deleteProduct(created.getId());
        assertThat(products.findById(created.getId()).orElseThrow().getStatus()).isEqualTo(ProductStatus.DELETED);
        assertThat(productService.createProduct(minimal("x", "dong-ho-test", null, List.of("c1"))).getSlug()).isEqualTo("dong-ho-test");
    }

    @Test
    void activatingRequiresPriceAndDeletingParentDeletesVariants() {
        var noPrice = productService.createProduct(new CreateProductRequest("No price", null, null, null, null, null, null,
                null, null, false, "INACTIVE", List.of("c1"), null, null, null, null));
        assertThatThrownBy(() -> productService.changeStatus(noPrice.getId(), ProductStatus.ACTIVE))
                .isInstanceOf(BadRequestException.class);

        productService.deleteProduct(IPHONE);
        assertThat(products.findVariants(IPHONE)).allSatisfy(v -> assertThat(v.getStatus()).isEqualTo(ProductStatus.DELETED));
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED) // mỗi lệnh lỗi tự rollback, không làm hỏng transaction chung
    void databaseRejectsInvalidCombinations() {
        assertThatThrownBy(() -> jdbc.update("""
                INSERT INTO variant_option_combination VALUES (1, 772266622382519, 369334726608713, 1865809232457271, 1965809232457273, 9)"""))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> jdbc.update("INSERT INTO products (id, name, parent_id) VALUES (2, 'x', 772266622382519)"))
                .isInstanceOf(DataIntegrityViolationException.class);
        String key = jdbc.queryForObject("SELECT variant_key FROM products WHERE id = 772266622382519", String.class);
        assertThatThrownBy(() -> jdbc.update("INSERT INTO products (id, name, parent_id, variant_key) VALUES (3, 'x', ?, ?)",
                Long.parseLong(IPHONE), key))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    private ProductUpserted lastUpserted(String productId) {
        byte[] payload = jdbc.queryForObject("""
                SELECT payload FROM outbox WHERE aggregateid = ? AND type LIKE '%ProductUpserted'
                ORDER BY created_at DESC, id DESC LIMIT 1""", byte[].class, productId);
        try {
            return ProductUpserted.parseFrom(payload);
        } catch (Exception notPlain) {
            try {
                return Any.parseFrom(payload).unpack(ProductUpserted.class);
            } catch (Exception e) {
                throw new IllegalStateException(e);
            }
        }
    }

    private static CreateProductRequest minimal(String name, String slug, String sku, List<String> categories) {
        return new CreateProductRequest(name, slug, sku, null, new BigDecimal("1"), "USD", null, null, null, false, null,
                categories, null, null, null, null);
    }
}
