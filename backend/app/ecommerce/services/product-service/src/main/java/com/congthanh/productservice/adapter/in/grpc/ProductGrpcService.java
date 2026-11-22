package com.congthanh.productservice.adapter.in.grpc;

import com.congthanh.productservice.domain.model.enums.ProductStatus;
import com.congthanh.productservice.grpc.GetProductsByIdsRequest;
import com.congthanh.productservice.grpc.GetProductsByIdsResponse;
import com.congthanh.productservice.grpc.GetProductsForOrderRequest;
import com.congthanh.productservice.grpc.GetProductsForOrderResponse;
import com.congthanh.productservice.grpc.ProductDetail;
import com.congthanh.productservice.grpc.ProductRequest;
import com.congthanh.productservice.grpc.ProductResponse;
import com.congthanh.productservice.grpc.ProductServiceGrpc;
import com.congthanh.productservice.application.dto.ProductDTO;
import com.congthanh.commonservice.exception.NotFoundException;
import com.congthanh.productservice.domain.model.Product;
import com.congthanh.productservice.application.port.in.ProductService;
import io.grpc.stub.StreamObserver;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.grpc.server.service.GrpcService;

import com.congthanh.ecommerce.common.v1.Money;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@GrpcService
@Slf4j
@RequiredArgsConstructor
public class ProductGrpcService extends ProductServiceGrpc.ProductServiceImplBase {

    private final ProductService productService;


    @Override
    public void getProductById(ProductRequest request, StreamObserver<ProductResponse> responseObserver) {
        ProductDTO product = productService.getProductById(request.getProductId());
        if (product != null) {
            ProductResponse response = ProductResponse.newBuilder()
                    .setId(product.getId())
                    .setName(product.getName())
                    .build();
            responseObserver.onNext(response);
            responseObserver.onCompleted();
        } else {
            responseObserver.onError(new NotFoundException("Product not found"));
        }
    }

    @Override
    public void getProductsForOrder(GetProductsForOrderRequest request, StreamObserver<GetProductsForOrderResponse> responseObserver) {
        GetProductsForOrderResponse.Builder responseBuilder = GetProductsForOrderResponse.newBuilder();

        try {
            List<String> productIds = request.getProductIdsList();
            List<Product> products = productService.findByIds(productIds);

            if (products.size() != productIds.size()) {
                responseBuilder.setErrorCode("NOT_FOUND")
                        .setErrorMessage("Some products not found");
            } else {
                List<ProductDetail> details = new ArrayList<>();
                for (Product p : products) {
                    if (p.status() != ProductStatus.ACTIVE) {
                        responseBuilder.setErrorCode("NOT_AVAILABLE")
                                .setErrorMessage("Product " + p.id() + " is not available");
                        break;
                    }
                    details.add(toDetail(p, true));
                }
                if (responseBuilder.getErrorCode().isEmpty()) {
                    responseBuilder.addAllProducts(details);
                }
            }
        } catch (RuntimeException e) {
            log.error("getProductsForOrder failed", e);
            responseBuilder.setErrorCode("INTERNAL_ERROR").setErrorMessage("internal error");
        }

        responseObserver.onNext(responseBuilder.build());
        responseObserver.onCompleted();
    }

    @Override
    public void getProductsByIds(GetProductsByIdsRequest request, StreamObserver<GetProductsByIdsResponse> responseObserver) {
        List<String> ids = List.copyOf(new LinkedHashSet<>(request.getProductIdsList()));
        if (ids.size() > 200) {
            responseObserver.onError(io.grpc.Status.INVALID_ARGUMENT
                    .withDescription("at most 200 product ids per call").asRuntimeException());
            return;
        }
        Map<String, Product> byId = productService.findByIds(ids).stream()
                .collect(Collectors.toMap(Product::id, Function.identity(), (a, b) -> a));
        GetProductsByIdsResponse.Builder response = GetProductsByIdsResponse.newBuilder();
        for (String id : ids) {
            Product p = byId.get(id);
            boolean active = p != null && p.status() == ProductStatus.ACTIVE;
            if (p == null || (!active && !request.getIncludeInactive())) {
                response.addNotFound(id);
            } else {
                response.addProducts(toDetail(p, active));
            }
        }
        responseObserver.onNext(response.build());
        responseObserver.onCompleted();
    }

    private static ProductDetail toDetail(Product p, boolean active) {
        ProductDetail.Builder b = ProductDetail.newBuilder()
                .setProductId(p.id())
                .setSku(p.sku() != null ? p.sku() : "")
                .setName(p.name())
                .setCurrency(p.price() != null ? p.price().currency() : "")
                .setIsActive(active)
                .setIsDeleted(p.status() == ProductStatus.DELETED)
                .setSellerId(p.supplier() != null ? p.supplier() : "")
                .setSellerActive(true);
        if (p.price() != null) {
            Money price = money(p.price().amount(), p.price().currency());
            // Giá bán cuối cùng theo bảng giá thuộc pricing-service; ở đây là giá niêm yết gốc.
            b.setOriginalPriceMoney(price).setSellingPriceMoney(price);
            // Trường double cũ giữ cho client chưa chuyển sang *_money (deprecated).
            b.setOriginalPrice(p.price().amount().doubleValue()).setSellingPrice(p.price().amount().doubleValue());
        }
        return b.build();
    }

    private static Money money(BigDecimal amount, String currency) {
        BigDecimal a = amount.setScale(9, RoundingMode.HALF_EVEN);
        BigDecimal units = a.setScale(0, RoundingMode.DOWN);
        return Money.newBuilder().setCurrencyCode(currency).setUnits(units.longValueExact())
                .setNanos(a.subtract(units).movePointRight(9).intValueExact()).build();
    }
}
