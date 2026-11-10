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
import com.congthanh.productservice.domain.exception.NotFoundException;
import com.congthanh.productservice.domain.model.Product;
import com.congthanh.productservice.application.port.out.product.ProductRepository;
import com.congthanh.productservice.application.port.in.ProductService;
import io.grpc.stub.StreamObserver;
import lombok.RequiredArgsConstructor;
import org.springframework.grpc.server.service.GrpcService;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@GrpcService
@RequiredArgsConstructor
public class ProductGrpcService extends ProductServiceGrpc.ProductServiceImplBase {

    private final ProductService productService;

    private final ProductRepository productRepository;

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
            List<Product> products = productRepository.findAllById(productIds);

            if (products.size() != productIds.size()) {
                responseBuilder.setErrorCode("NOT_FOUND")
                        .setErrorMessage("Some products not found");
            } else {
                List<ProductDetail> details = new ArrayList<>();
                for (Product p : products) {
                    if (p.getStatus() != ProductStatus.ACTIVE) {
                        responseBuilder.setErrorCode("NOT_AVAILABLE")
                                .setErrorMessage("Product " + p.getId() + " is not available");
                        break;
                    }
                    // Seller status, shipping restrictions, per-order limits and pre-orders are not
                    // modelled on Product yet; add those checks here once the fields exist.
                    double price = p.getPrice() != null ? p.getPrice().doubleValue() : 0d;
                    details.add(ProductDetail.newBuilder()
                            .setProductId(p.getId())
                            .setSku(p.getSku() != null ? p.getSku() : "")
                            .setName(p.getName())
                            .setOriginalPrice(price)
                            .setSellingPrice(price)
                            .setIsActive(true)
                            .setIsDeleted(false)
                            .setSellerId(p.getSupplier() != null ? p.getSupplier() : "")
                            .setSellerActive(true)
                            .build());
                }
                if (responseBuilder.getErrorCode().isEmpty()) {
                    responseBuilder.addAllProducts(details);
                }
            }
        } catch (Exception e) {
            responseBuilder.setErrorCode("INTERNAL_ERROR")
                    .setErrorMessage(e.getMessage());
        }

        responseObserver.onNext(responseBuilder.build());
        responseObserver.onCompleted();
    }

    /**
     * Tra cứu hàng loạt cho các service NestJS (catalog kiểm tra quyền gắn tag, BFF ghép dữ liệu).
     * Giữ thứ tự id đầu vào, id không tồn tại (hoặc không active khi include_inactive=false) trả về not_found.
     */
    @Override
    public void getProductsByIds(GetProductsByIdsRequest request, StreamObserver<GetProductsByIdsResponse> responseObserver) {
        List<String> ids = List.copyOf(new LinkedHashSet<>(request.getProductIdsList()));
        if (ids.size() > 200) {
            responseObserver.onError(io.grpc.Status.INVALID_ARGUMENT
                    .withDescription("at most 200 product ids per call").asRuntimeException());
            return;
        }
        Map<String, Product> byId = productRepository.findAllById(ids).stream()
                .collect(Collectors.toMap(Product::getId, Function.identity(), (a, b) -> a));
        GetProductsByIdsResponse.Builder response = GetProductsByIdsResponse.newBuilder();
        for (String id : ids) {
            Product p = byId.get(id);
            boolean active = p != null && p.getStatus() == ProductStatus.ACTIVE;
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
        double price = p.getPrice() != null ? p.getPrice().doubleValue() : 0d;
        return ProductDetail.newBuilder()
                .setProductId(p.getId())
                .setSku(p.getSku() != null ? p.getSku() : "")
                .setName(p.getName())
                .setOriginalPrice(price)
                .setSellingPrice(price)
                .setIsActive(active)
                .setIsDeleted(false)
                .setSellerId(p.getSupplier() != null ? p.getSupplier() : "")
                .setSellerActive(true)
                .build();
    }
}
