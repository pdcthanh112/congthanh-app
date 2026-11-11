package com.congthanh.checkoutservice.adapter.out.grpc;

import com.congthanh.checkoutservice.application.port.out.Catalog;
import com.congthanh.productservice.grpc.GetProductsForOrderRequest;
import com.congthanh.productservice.grpc.ProductDetail;
import com.congthanh.productservice.grpc.ProductServiceGrpc;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import io.grpc.StatusRuntimeException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.grpc.client.GrpcChannelFactory;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

@Component
class CatalogGrpcAdapter implements Catalog {

    private final ProductServiceGrpc.ProductServiceBlockingStub stub;
    private final int defaultWeight;
    private final long defaultTaxClass;

    CatalogGrpcAdapter(GrpcChannelFactory channels,
                       @Value("${checkout.default-weight-grams:500}") int defaultWeight,
                       @Value("${checkout.default-tax-class-id:1}") long defaultTaxClass) {
        this.stub = ProductServiceGrpc.newBlockingStub(channels.createChannel("product"));
        this.defaultWeight = defaultWeight;
        this.defaultTaxClass = defaultTaxClass;
    }

    @Override
    @Retry(name = "product")
    @CircuitBreaker(name = "product")
    public Map<String, Product> lookup(Collection<String> productIds) {
        try {
            var res = stub.withDeadlineAfter(2, TimeUnit.SECONDS)
                    .getProductsForOrder(GetProductsForOrderRequest.newBuilder().addAllProductIds(productIds).build());
            Map<String, Product> out = new HashMap<>();
            for (ProductDetail p : res.getProductsList()) {
                boolean purchasable = p.getIsActive() && !p.getIsDeleted() && (p.getSellerId().isEmpty() || p.getSellerActive());
                out.put(p.getProductId(), new Product(p.getProductId(), p.getSku(), p.getName(), purchasable,
                        p.getMaxQuantityPerOrder(), intAttr(p, "weight_grams", defaultWeight),
                        intAttr(p, "tax_class_id", (int) defaultTaxClass)));
            }
            return out;
        } catch (StatusRuntimeException e) {
            throw Protos.translate("product-service", e);
        }
    }

    private static int intAttr(ProductDetail p, String key, int fallback) {
        try {
            String v = p.getAttributesMap().get(key);
            return v == null || v.isBlank() ? fallback : Integer.parseInt(v.trim());
        } catch (NumberFormatException e) {
            return fallback;
        }
    }
}
