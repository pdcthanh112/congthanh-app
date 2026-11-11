package com.congthanh.productservice.adapter.out.readmodel;

import com.congthanh.productservice.application.port.out.ProductReadModelStore;
import com.congthanh.productservice.application.readmodel.ProductDocument;
import org.bson.Document;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.aggregation.AggregationOperation;
import org.springframework.data.mongodb.core.aggregation.AggregationUpdate;
import org.springframework.data.mongodb.core.index.Index;
import org.springframework.data.mongodb.core.index.PartialIndexFilter;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

import static org.springframework.data.mongodb.core.query.Criteria.where;
import static org.springframework.data.mongodb.core.query.Query.query;

@Component
class MongoProductReadModelStore implements ProductReadModelStore {

    static final String COLLECTION = "product";
    private static final Logger log = LoggerFactory.getLogger(MongoProductReadModelStore.class);

    private final MongoTemplate mongo;

    MongoProductReadModelStore(MongoTemplate mongo) {
        this.mongo = mongo;
    }

    /** Tạo index khi khởi động; Mongo chưa sẵn sàng thì chỉ ghi log (lần khởi động sau tạo tiếp). */
    @EventListener(ApplicationReadyEvent.class)
    void ensureIndexes() {
        try {
            var ops = mongo.indexOps(COLLECTION);
            // partial: document mới có giá/tồn kho (chưa có phần lõi) không có slug, không được chiếm chỗ trong index unique.
            ops.createIndex(new Index("slug", Sort.Direction.ASC).named("slug_unique").unique()
                    .partial(PartialIndexFilter.of(where("slug").exists(true))));
            ops.createIndex(new Index("categories.id", Sort.Direction.ASC).named("category_id"));
            ops.createIndex(new Index("brand.id", Sort.Direction.ASC).named("brand_id").sparse());
        } catch (DataAccessException e) {
            log.warn("cannot create read model indexes: {}", e.toString());
        }
    }

    @Override
    public Optional<ProductDocument> findById(String id) {
        return findOne(query(where("_id").is(id).and("coreVersion").exists(true)));
    }

    @Override
    public Optional<ProductDocument> findBySlug(String slug) {
        return findOne(query(where("slug").is(slug).and("coreVersion").exists(true)));
    }

    private Optional<ProductDocument> findOne(Query q) {
        try {
            return Optional.ofNullable(mongo.findOne(q, ProductDocument.class, COLLECTION));
        } catch (DataAccessException e) {
            log.warn("read model unavailable, falling back to Postgres: {}", e.toString());
            return Optional.empty();
        }
    }

    @Override
    public boolean upsertCore(ProductDocument d) {
        long version = Objects.requireNonNull(d.getCoreVersion(), "coreVersion");
        Query q = query(where("_id").is(d.getId()).orOperator(
                where("coreVersion").lt(version), where("coreVersion").exists(false)));
        Update u = new Update()
                .set("name", d.getName())
                .set("sku", d.getSku())
                .set("gtin", d.getGtin())
                .set("description", d.getDescription())
                .set("status", d.getStatus())
                .set("featured", d.isFeatured())
                .set("hasVariant", d.isHasVariant())
                .set("parentId", d.getParentId())
                .set("supplier", d.getSupplier())
                .set("brand", keepBrandName(d))
                .set("categories", d.getCategories())
                .set("thumbnail", d.getThumbnail())
                .set("images", d.getImages())
                .set("attributes", d.getAttributes())
                .set("variants", d.getVariants())
                .set("basePrice", d.getBasePrice())
                .set("currency", d.getCurrency())
                .set("coreVersion", version)
                .set("updatedAt", Instant.now());
        if (d.getSlug() == null) {
            u.unset("slug");
        } else {
            u.set("slug", d.getSlug());
        }
        try {
            mongo.upsert(q, u, COLLECTION);
            return true;
        } catch (DuplicateKeyException e) {
            if (!isSlugConflict(e)) {
                return false; // bản đang lưu mới hơn
            }
            // Slug đã chuyển sang sản phẩm này (Postgres bảo đảm unique); document khác còn giữ slug là dữ liệu cũ.
            mongo.updateMulti(query(where("slug").is(d.getSlug()).and("_id").ne(d.getId())),
                    new Update().unset("slug"), COLLECTION);
            try {
                mongo.upsert(q, u, COLLECTION);
                return true;
            } catch (DuplicateKeyException again) {
                return false;
            }
        }
    }

    private static boolean isSlugConflict(DuplicateKeyException e) {
        return String.valueOf(e.getMessage()).contains("slug_unique");
    }

    /** Tên thương hiệu đến từ BrandUpserted (catalog-service); dựng lại phần lõi không được xoá mất nó. */
    private ProductDocument.Brand keepBrandName(ProductDocument d) {
        if (d.getBrand() == null) {
            return null;
        }
        Query q = query(where("_id").is(d.getId()));
        q.fields().include("brand");
        ProductDocument current = mongo.findOne(q, ProductDocument.class, COLLECTION);
        if (current != null && current.getBrand() != null
                && Objects.equals(current.getBrand().id(), d.getBrand().id())) {
            return current.getBrand();
        }
        return d.getBrand();
    }

    @Override
    public void delete(String id) {
        mongo.remove(query(where("_id").is(id)), COLLECTION);
    }

    @Override
    public void applyPrice(String productId, String priceList, BigDecimal amount, String currency, Instant at) {
        String list = safe(priceList);
        guardedUpsert(productId, "price_" + list, at,
                new Update().set("prices." + list, new ProductDocument.Price(amount, currency, at)));
    }

    @Override
    public void applyRating(String productId, double average, long count, List<Long> stars, Instant at) {
        guardedUpsert(productId, "rating", at, new Update().set("rating", new ProductDocument.Rating(average, count, stars)));
    }

    @Override
    public void applyStock(String productId, String warehouseId, int available, Instant at) {
        String wh = safe(warehouseId);
        String key = "stock_" + wh;
        long ms = at.toEpochMilli();
        // Pipeline update: đặt tồn kho của kho này rồi tính lại inStock = tổng mọi kho > 0, trong cùng một lệnh.
        AggregationOperation setStock = ctx -> new Document("$set", new Document("stock." + wh, available)
                .append("versions." + key, ms));
        AggregationOperation setInStock = ctx -> new Document("$set", new Document("inStock",
                new Document("$gt", List.of(new Document("$sum", new Document("$map",
                        new Document("input", new Document("$objectToArray", "$stock")).append("in", "$$this.v"))), 0))));
        try {
            mongo.upsert(guard(productId, key, ms), AggregationUpdate.from(List.of(setStock, setInStock)), COLLECTION);
        } catch (DuplicateKeyException stale) {
            // đã có mốc mới hơn
        }
    }

    @Override
    public void applyCategory(String categoryId, String name, String slug, Instant at) {
        String key = "versions.category_" + safe(categoryId);
        long ms = at.toEpochMilli();
        Query q = query(where("categories.id").is(categoryId).orOperator(where(key).lt(ms), where(key).exists(false)));
        Update u = new Update()
                .set("categories.$[c].name", name)
                .set("categories.$[c].slug", slug)
                .set(key, ms)
                .filterArray(Criteria.where("c.id").is(categoryId));
        mongo.updateMulti(q, u, COLLECTION);
    }

    @Override
    public void applyBrand(long brandId, String name, String slug, Instant at) {
        long ms = at.toEpochMilli();
        Query q = query(where("brand.id").is(brandId).orOperator(
                where("versions.brand").lt(ms), where("versions.brand").exists(false)));
        mongo.updateMulti(q, new Update().set("brand.name", name).set("brand.slug", slug).set("versions.brand", ms),
                COLLECTION);
    }

    private void guardedUpsert(String productId, String key, Instant at, Update update) {
        long ms = at.toEpochMilli();
        try {
            mongo.upsert(guard(productId, key, ms), update.set("versions." + key, ms), COLLECTION);
        } catch (DuplicateKeyException stale) {
            // đã có mốc mới hơn
        }
    }

    static Query guard(String productId, String key, long ms) {
        return query(where("_id").is(productId).orOperator(
                where("versions." + key).lt(ms), where("versions." + key).exists(false)));
    }

    /** Khoá trong Mongo không được chứa '.' hay bắt đầu bằng '$'. */
    static String safe(String key) {
        return key == null || key.isEmpty() ? "_" : key.replace('.', '_').replace('$', '_');
    }
}
