package com.congthanh.productservice.application.service;

import com.congthanh.productservice.application.port.in.ProductReadModelUseCase;
import com.congthanh.productservice.application.port.out.ProductReadModelStore;
import com.congthanh.productservice.application.port.out.ProductRepository;
import com.congthanh.productservice.application.port.out.ProductSnapshot;
import com.congthanh.productservice.application.port.out.ProductSnapshots;
import com.congthanh.productservice.application.readmodel.ProductDocument;
import com.congthanh.commonservice.exception.NotFoundException;
import com.congthanh.productservice.domain.model.enums.ProductStatus;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;

@Service
@Slf4j
public class ProductReadModelService implements ProductReadModelUseCase {

    private static final int REBUILD_BATCH = 200;

    private final ProductSnapshots snapshots;
    private final ProductRepository products;
    private final ProductReadModelStore store;
    private final ProductDocumentAssembler assembler;
    private final TransactionTemplate snapshotTx;

    public ProductReadModelService(ProductSnapshots snapshots, ProductRepository products, ProductReadModelStore store,
                                   ProductDocumentAssembler assembler, PlatformTransactionManager txManager) {
        this.snapshots = snapshots;
        this.products = products;
        this.store = store;
        this.assembler = assembler;
        // REPEATABLE READ: mọi câu SELECT thấy cùng một snapshot, lấy tại câu đầu tiên (snapshotMicros) — chính
        // là coreVersion. Snapshot sau luôn mới hơn snapshot trước nên so coreVersion là đủ để không ghi lùi.
        this.snapshotTx = new TransactionTemplate(txManager);
        this.snapshotTx.setReadOnly(true);
        this.snapshotTx.setIsolationLevel(TransactionDefinition.ISOLATION_REPEATABLE_READ);
    }

    @Override
    public ProductDocument getById(String id) {
        return read(store.findById(id), r -> r.byId(id));
    }

    @Override
    public ProductDocument getBySlug(String slug) {
        return read(store.findBySlug(slug), r -> r.bySlug(slug));
    }

    private ProductDocument read(Optional<ProductDocument> cached, Function<ProductSnapshots, Optional<ProductSnapshot>> load) {
        ProductDocument doc = cached.orElseGet(() -> snapshotTx.execute(s -> {
            long version = snapshots.snapshotMicros();
            return load.apply(snapshots).map(p -> assembler.assemble(p, version)).orElse(null);
        }));
        if (doc == null || doc.getStatus() == ProductStatus.DELETED) {
            throw new NotFoundException("Product not found");
        }
        return doc;
    }

    @Override
    public void refresh(String productId) {
        String parentId = refreshOne(productId);
        if (parentId != null) {
            refreshOne(parentId);
        }
    }

    /** @return id sản phẩm cha nếu đây là variant (document cha nhúng danh sách variant). */
    private String refreshOne(String productId) {
        ProductDocument doc = snapshotTx.execute(s -> {
            long version = snapshots.snapshotMicros();
            return snapshots.byId(productId).map(p -> assembler.assemble(p, version)).orElse(null);
        });
        if (doc == null) {
            store.delete(productId);
            return null;
        }
        if (!store.upsertCore(doc)) {
            log.debug("read model for {} already newer than {}", productId, doc.getCoreVersion());
        }
        return doc.getParentId();
    }

    @Override
    public void onPriceChanged(String productId, String priceList, BigDecimal amount, String currency, Instant at) {
        store.applyPrice(productId, priceList, amount, currency, at);
    }

    @Override
    public void onRatingChanged(String productId, double average, long count, List<Long> stars, Instant at) {
        store.applyRating(productId, average, count, stars, at);
    }

    @Override
    public void onStockChanged(String productId, String warehouseId, int available, Instant at) {
        store.applyStock(productId, warehouseId, available, at);
    }

    @Override
    public void onCategoryChanged(String categoryId, String name, String slug, Instant at) {
        store.applyCategory(categoryId, name, slug, at);
    }

    @Override
    public void onBrandChanged(long brandId, String name, String slug, Instant at) {
        store.applyBrand(brandId, name, slug, at);
    }

    @Override
    public RebuildResult rebuildAll() {
        int ok = 0;
        int failed = 0;
        String after = "0"; // id Snowflake luôn > 0
        while (true) {
            List<String> ids = products.idsAfter(after, REBUILD_BATCH);
            if (ids.isEmpty()) {
                break;
            }
            for (String id : ids) {
                try {
                    refreshOne(id);
                    ok++;
                } catch (RuntimeException e) {
                    failed++;
                    log.warn("rebuild read model failed for product {}: {}", id, e.toString());
                }
            }
            after = ids.getLast();
        }
        log.info("product read model rebuilt: {} ok, {} failed", ok, failed);
        return new RebuildResult(ok, failed);
    }
}
