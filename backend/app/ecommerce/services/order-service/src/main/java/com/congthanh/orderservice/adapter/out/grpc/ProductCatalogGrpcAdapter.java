package com.congthanh.orderservice.adapter.out.grpc;

import com.congthanh.orderservice.application.port.out.ProductCatalog;
import com.congthanh.orderservice.domain.model.Money;
import com.congthanh.productservice.grpc.GetProductsForOrderRequest;
import com.congthanh.productservice.grpc.ProductDetail;
import com.congthanh.productservice.grpc.ProductServiceGrpc;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import io.grpc.StatusRuntimeException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.grpc.client.GrpcChannelFactory;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/** Gọi product-service (gRPC). Resilience4j: retry lỗi tạm thời, circuit breaker khi product-service sập. */
@Component
class ProductCatalogGrpcAdapter implements ProductCatalog {

    private final ProductServiceGrpc.ProductServiceBlockingStub stub;
    private final String currency;

    ProductCatalogGrpcAdapter(GrpcChannelFactory channels, @Value("${order.currency:VND}") String currency) {
        this.stub = ProductServiceGrpc.newBlockingStub(channels.createChannel("product"));
        this.currency = currency;
    }

    @Override
    @Retry(name = "product")
    @CircuitBreaker(name = "product")
    public Map<String, ProductSnapshot> lookup(Collection<String> productIds) {
        try {
            var res = stub.withDeadlineAfter(2, TimeUnit.SECONDS)
                    .getProductsForOrder(GetProductsForOrderRequest.newBuilder().addAllProductIds(productIds).build());
            Map<String, ProductSnapshot> out = new HashMap<>();
            for (ProductDetail p : res.getProductsList()) {
                boolean purchasable = p.getIsActive() && !p.getIsDeleted() && (p.getSellerId().isEmpty() || p.getSellerActive());
                // Giá bán cuối cùng do product-service tính; client không bao giờ gửi giá.
                Money price = new Money(BigDecimal.valueOf(p.getSellingPrice()), currency);
                out.put(p.getProductId(), new ProductSnapshot(p.getProductId(), p.getSku(), p.getName(), price,
                        purchasable, p.getMaxQuantityPerOrder()));
            }
            return out;
        } catch (StatusRuntimeException e) {
            throw Protos.translate("product-service", e);
        }
    }

}
