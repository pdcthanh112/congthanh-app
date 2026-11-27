package com.congthanh.productservice.domain.model;

import com.congthanh.commonservice.exception.BadRequestException;
import com.congthanh.commonservice.exception.ConflictException;
import com.congthanh.commonservice.exception.NotFoundException;
import com.congthanh.productservice.domain.event.ProductEvent;
import com.congthanh.productservice.domain.model.enums.AttributeDataType;
import com.congthanh.productservice.domain.model.enums.ProductStatus;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ProductTest {

    final AtomicLong ids = new AtomicLong(100);

    @Test
    void createdProductIsInactiveWithFirstImageAsThumbnailAndOneChangeEvent() {
        Product p = shirt();

        assertThat(p.status()).isEqualTo(ProductStatus.INACTIVE);
        assertThat(p.thumbnailId()).isEqualTo(1L);
        assertThat(p.categories()).extracting(CategoryAssignment::categoryId).containsExactly("c1", "c2");
        assertThat(p.pullEvents()).containsExactly(new ProductEvent.ProductChanged("p1"));
        assertThat(p.pullEvents()).isEmpty();
    }

    @Test
    void createRejectsInvalidInput() {
        assertThatThrownBy(() -> Product.create(draft("Bad Slug", List.of("c1")))).isInstanceOf(BadRequestException.class);
        assertThatThrownBy(() -> Product.create(draft("shirt", List.of()))).isInstanceOf(BadRequestException.class)
                .hasMessageContaining("category");
        assertThatThrownBy(() -> Money.ofNullable(BigDecimal.ONE, null)).isInstanceOf(BadRequestException.class);
        assertThatThrownBy(() -> Money.ofNullable(BigDecimal.ONE.negate(), "USD")).isInstanceOf(BadRequestException.class);
    }

    @Test
    void activationNeedsPriceOrSellableVariant() {
        Product noPrice = Product.create(new Product.Draft("p2", "Free", "free", null, null, null, null, null, null, false,
                List.of("c1"), List.of(), null, null));
        assertThatThrownBy(() -> noPrice.activate(false)).isInstanceOf(BadRequestException.class);

        Product p = shirt();
        p.activate(false);
        assertThat(p.status()).isEqualTo(ProductStatus.ACTIVE);
        assertThatThrownBy(() -> p.reprice(null)).isInstanceOf(BadRequestException.class);
    }

    @Test
    void variantsUseDeclaredValuesSameAxesAndOneLevel() {
        Product parent = shirt();
        OptionValue black = parent.declareOptionValue(5L, "Black", ids::incrementAndGet);
        OptionValue m = parent.declareOptionValue(6L, "M", ids::incrementAndGet);
        assertThat(parent.declareOptionValue(5L, "black", ids::incrementAndGet)).isEqualTo(black);
        parent.pullEvents();

        Product v = parent.createVariant(variant("v1", "SKU-1"), List.of(black, m), Set.of());

        assertThat(v.parentId()).isEqualTo("p1");
        assertThat(v.name()).isEqualTo("Shirt Black M");
        assertThat(v.variantKey()).isEqualTo(Product.variantKey(List.of(m, black)));
        assertThat(v.selections()).extracting(OptionSelection::optionId).containsExactly(5L, 6L);
        assertThat(parent.hasVariant()).isTrue();
        assertThat(parent.pullEvents()).containsExactly(new ProductEvent.ProductChanged("p1"));

        assertThatThrownBy(() -> parent.createVariant(variant("v2", "SKU-2"), List.of(black), Set.of(5L, 6L)))
                .isInstanceOf(BadRequestException.class).hasMessageContaining("same options");
        assertThatThrownBy(() -> parent.createVariant(variant("v3", "SKU-3"), List.of(black, black), Set.of()))
                .isInstanceOf(BadRequestException.class).hasMessageContaining("only once");
        assertThatThrownBy(() -> v.createVariant(variant("v4", "SKU-4"), List.of(black), Set.of()))
                .isInstanceOf(BadRequestException.class);
        assertThatThrownBy(() -> parent.createVariant(variant("v5", "SKU-5"),
                List.of(new OptionValue(999L, 5L, "Pink", 9)), Set.of()))
                .isInstanceOf(BadRequestException.class).hasMessageContaining("not declared");
    }

    @Test
    void categoriesImagesAndAttributesKeepInvariants() {
        Product p = shirt();
        p.assignCategory("c3");
        assertThatThrownBy(() -> p.assignCategory("c3")).isInstanceOf(ConflictException.class);
        p.replaceCategories(List.of("c9"));
        assertThatThrownBy(() -> p.removeCategory("c9")).isInstanceOf(BadRequestException.class);

        p.setThumbnail(2L);
        p.removeImage(2L);
        assertThat(p.thumbnailId()).isEqualTo(1L);
        assertThatThrownBy(() -> p.setThumbnail(42L)).isInstanceOf(BadRequestException.class);

        ProductAttribute pages = ProductAttribute.define(9L, new ProductAttribute.Spec("pages", "Pages", AttributeDataType.NUMBER,
                null, true, false, 0, null, null));
        p.addAttribute(pages.valuesOf(List.of("225")));
        assertThatThrownBy(() -> p.addAttribute(pages.valuesOf(List.of("1")))).isInstanceOf(ConflictException.class);
        assertThatThrownBy(() -> pages.valuesOf(List.of("many"))).isInstanceOf(BadRequestException.class).hasMessageContaining("Pages");
        assertThat(p.attributes()).singleElement().satisfies(a -> assertThat(a.number()).isEqualByComparingTo("225"));
    }

    @Test
    void deleteEmitsOnlyDeletedAndBlocksFurtherChanges() {
        Product p = shirt();
        p.rename("New");

        p.delete();

        assertThat(p.pullEvents()).containsExactly(new ProductEvent.ProductDeleted("p1"));
        assertThatThrownBy(() -> p.rename("Again")).isInstanceOf(NotFoundException.class);
        assertThatThrownBy(p::delete).isInstanceOf(NotFoundException.class);
    }

    @Test
    void staleVersionIsAConflict() {
        assertThatThrownBy(() -> shirt().requireVersion(3L)).isInstanceOf(ConflictException.class);
    }

    @Test
    void attributeTypeCannotChangeOnceUsed() {
        ProductAttribute a = ProductAttribute.define(1L, new ProductAttribute.Spec(null, "Note", null, null, false, false, 0, null, null));
        assertThatThrownBy(() -> a.redefine(new ProductAttribute.Spec(null, "Note", AttributeDataType.NUMBER, null, false, false, 0, null,
                null), true))
                .isInstanceOf(ConflictException.class);
        assertThatThrownBy(() -> ProductAttribute.define(2L, new ProductAttribute.Spec(null, "Ram", null, null, true, false, 0, null, null)))
                .isInstanceOf(BadRequestException.class).hasMessageContaining("code");
    }

    @Test
    void multiSelectAttributeKeepsSeveralDistinctValuesAndChangesReplaceThemAll() {
        Product p = shirt();
        ProductAttribute style = ProductAttribute.define(3L, new ProductAttribute.Spec("style", "Phong cách",
                AttributeDataType.MULTI_SELECT, null, true, false, 0, null, null));

        p.addAttribute(style.valuesOf(List.of("Thể thao", "Hàn Quốc", "thể thao")));
        assertThat(p.attributes()).extracting(AttributeValue::value).containsExactly("Thể thao", "Hàn Quốc");

        p.changeAttribute(style.valuesOf(List.of("Cơ bản")));
        assertThat(p.attributes()).extracting(AttributeValue::value).containsExactly("Cơ bản");
    }

    @Test
    void singleValueAttributeRejectsSeveralValues() {
        ProductAttribute material = ProductAttribute.define(4L, new ProductAttribute.Spec(null, "Chất liệu", AttributeDataType.TEXT,
                null, false, false, 0, null, null));

        assertThatThrownBy(() -> material.valuesOf(List.of("Cotton", "Linen")))
                .isInstanceOf(BadRequestException.class).hasMessageContaining("single value");
    }

    @Test
    void allowedValuesAreEnforcedAndRewrittenToTheirSpelling() {
        ProductAttribute collar = ProductAttribute.define(5L, new ProductAttribute.Spec(null, "Cổ áo", AttributeDataType.TEXT,
                null, false, false, 0, null, List.of("Cổ tròn", "Cổ tim")));

        assertThat(collar.valuesOf(List.of("cổ TRÒN"))).extracting(AttributeValue::value).containsExactly("Cổ tròn");
        assertThatThrownBy(() -> collar.valuesOf(List.of("Cổ bẻ")))
                .isInstanceOf(BadRequestException.class).hasMessageContaining("not an allowed value");
    }

    @Test
    void allowedValuesOnlyForTextAttributesAndMustBeDistinct() {
        assertThatThrownBy(() -> ProductAttribute.define(6L, new ProductAttribute.Spec(null, "Ram", AttributeDataType.NUMBER, "GB",
                false, false, 0, null, List.of("8", "16"))))
                .isInstanceOf(BadRequestException.class).hasMessageContaining("TEXT and MULTI_SELECT");
        assertThatThrownBy(() -> ProductAttribute.define(7L, new ProductAttribute.Spec(null, "Màu", AttributeDataType.TEXT, null,
                false, false, 0, null, List.of("Đỏ", "đỏ"))))
                .isInstanceOf(BadRequestException.class).hasMessageContaining("duplicate");
    }

    @Test
    void categoryTemplateRejectsDuplicateAttributes() {
        var items = List.of(new CategoryAttributeTemplate.Item(1L, true, 0), new CategoryAttributeTemplate.Item(1L, false, 1));

        assertThatThrownBy(() -> new CategoryAttributeTemplate("cat-1", items))
                .isInstanceOf(BadRequestException.class).hasMessageContaining("twice");
    }

    @Test
    void specificationsAreNormalisedAndEmptyGroupsDropped() {
        Specifications specs = Specifications.of(List.of(
                new Specifications.Group(" Màn hình ", List.of(new Specifications.Item(" Kích thước ", " 6.7 inch "))),
                new Specifications.Group("Trống", List.of()),
                new Specifications.Group(null, List.of(new Specifications.Item("Bảo hành", "12 tháng")))));

        assertThat(specs.groups()).containsExactly(
                new Specifications.Group("Màn hình", List.of(new Specifications.Item("Kích thước", "6.7 inch"))),
                new Specifications.Group(null, List.of(new Specifications.Item("Bảo hành", "12 tháng"))));
    }

    @Test
    void specificationsRejectBlankValuesAndDuplicateRows() {
        assertThatThrownBy(() -> Specifications.of(List.of(new Specifications.Group("Pin",
                List.of(new Specifications.Item("Dung lượng", " "))))))
                .isInstanceOf(BadRequestException.class).hasMessageContaining("group 1 row 1 value");
        assertThatThrownBy(() -> Specifications.of(List.of(new Specifications.Group("Pin", List.of(
                new Specifications.Item("Dung lượng", "5000 mAh"), new Specifications.Item("dung lượng", "4000 mAh"))))))
                .isInstanceOf(BadRequestException.class).hasMessageContaining("duplicate");
    }

    @Test
    void onlyParentProductsCarrySpecifications() {
        Product parent = shirt();
        OptionValue black = parent.declareOptionValue(5L, "Black", ids::incrementAndGet);
        Product variant = parent.createVariant(variant("v1", "SKU-1"), List.of(black), Set.of());
        Specifications specs = Specifications.of(List.of(new Specifications.Group(null,
                List.of(new Specifications.Item("Xuất xứ", "Việt Nam")))));

        parent.changeSpecifications(specs);

        assertThat(parent.specifications()).isEqualTo(specs);
        assertThatThrownBy(() -> variant.changeSpecifications(specs)).isInstanceOf(BadRequestException.class);
    }

    @Test
    void attributeGroupNeedsAName() {
        assertThatThrownBy(() -> AttributeGroup.define(1L, "  ", 0)).isInstanceOf(BadRequestException.class);
        assertThat(AttributeGroup.define(1L, " Màn hình ", 2).name()).isEqualTo("Màn hình");
    }

    @Test
    void sellerOwnsProductAndItsVariantsWhilePlatformProductsBelongToNoSeller() {
        Product parent = shirt();
        OptionValue black = parent.declareOptionValue(5L, "Black", ids::incrementAndGet);

        Product v = parent.createVariant(variant("v1", "SKU-1"), List.of(black), Set.of());

        assertThat(parent.isOwnedBy("seller-1")).isTrue();
        assertThat(parent.isOwnedBy("seller-2")).isFalse();
        assertThat(v.isOwnedBy("seller-1")).isTrue();
        Product platform = Product.create(draft("platform", List.of("c1")));
        assertThat(platform.ownerId()).isNull();
        assertThat(platform.isOwnedBy(null)).isFalse();
    }

    private Product shirt() {
        return Product.create(new Product.Draft("p1", "Shirt", "shirt", "SKU-P1", null, new Money(new BigDecimal("10"), "USD"),
                null, null, null, false, List.of("c1", "c2", "c1"),
                List.of(new ProductImage(1L, "a.jpg", 1), new ProductImage(2L, "b.jpg", 2)), null, "seller-1"));
    }

    private static Product.Draft draft(String slug, List<String> categories) {
        return new Product.Draft("p9", "X", slug, null, null, null, null, null, null, false, categories, List.of(), null, null);
    }

    private static Product.VariantDraft variant(String id, String sku) {
        return new Product.VariantDraft(id, null, sku, null, new Money(BigDecimal.TEN, "USD"), null);
    }
}
