package com.congthanh.productservice.application.service;

import com.congthanh.productservice.application.port.in.ProductEventReplayUseCase;
import com.congthanh.productservice.application.port.out.ProductRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.List;

@Service
@Slf4j
public class ProductEventReplayService implements ProductEventReplayUseCase {

    private static final int BATCH = 200;

    private final ProductRepository products;
    private final TransactionTemplate tx;

    public ProductEventReplayService(ProductRepository products, PlatformTransactionManager txManager) {
        this.products = products;
        this.tx = new TransactionTemplate(txManager);
    }

    @Override
    public Result replayAll() {
        int published = 0;
        int failed = 0;
        String after = "0"; // id Snowflake luôn > 0
        while (true) {
            List<String> ids = products.idsAfter(after, BATCH);
            if (ids.isEmpty()) {
                break;
            }
            for (String id : ids) {
                try {
                    // Outbox ghi trong transaction này nên event chỉ ra khi transaction commit.
                    tx.executeWithoutResult(s -> products.republish(id));
                    published++;
                } catch (RuntimeException e) {
                    failed++;
                    log.warn("replay product event failed for {}: {}", id, e.toString());
                }
            }
            after = ids.getLast();
        }
        log.info("product events replayed: {} published, {} failed", published, failed);
        return new Result(published, failed);
    }
}
