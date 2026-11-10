package com.congthanh.productservice.adapter.out.readmodel;

import com.congthanh.productservice.application.event.ProductChanged;
import com.congthanh.productservice.application.port.out.product.ProductDocumentRepository;
import com.congthanh.productservice.application.readmodel.ProductDocument;
import com.congthanh.productservice.domain.model.enums.ProductStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Read model MongoDB cho trang sản phẩm (CQRS). Cập nhật SAU commit: lỗi Mongo không làm hỏng ghi
 * Postgres; read model có thể trễ (eventual consistency) và được dựng lại bằng cách phát lại outbox.
 */
@Component
class ProductReadModelProjector {

    private static final Logger log = LoggerFactory.getLogger(ProductReadModelProjector.class);
    private final ProductDocumentRepository documents;

    ProductReadModelProjector(ProductDocumentRepository documents) {
        this.documents = documents;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    void on(ProductChanged e) {
        try {
            if (e.deleted()) {
                documents.deleteById(e.productId());
                return;
            }
            ProductDocument doc = documents.findById(e.productId()).orElseGet(ProductDocument::new);
            doc.setId(e.productId());
            doc.setName(e.name());
            doc.setSlug(e.slug());
            doc.setSku(e.sku());
            doc.setPrice(e.price());
            doc.setDescription(e.description());
            doc.setHasVariant(e.hasVariant());
            doc.setFeatured(e.featured());
            doc.setStatus(e.status() == null ? null : ProductStatus.valueOf(e.status()));
            documents.save(doc);
        } catch (RuntimeException ex) {
            log.warn("read model update failed for product {}: {}", e.productId(), ex.toString());
        }
    }
}
