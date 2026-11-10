package com.congthanh.productservice.adapter.out.grpc;

import com.congthanh.catalogservice.grpc.CategoryRequest;
import com.congthanh.catalogservice.grpc.CategoryResponse;
import com.congthanh.catalogservice.grpc.CategoryServiceGrpc;
import com.congthanh.catalogservice.grpc.CategorySlugRequest;
import com.congthanh.catalogservice.grpc.ListCategoryRequest;
import com.congthanh.catalogservice.grpc.ValidateCategoriesRequest;
import com.congthanh.catalogservice.grpc.ValidateCategoriesResponse;
import com.congthanh.productservice.application.port.out.CategoryCatalog;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import org.springframework.grpc.client.GrpcChannelFactory;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.concurrent.TimeUnit;

/** catalog-service qua gRPC; kênh "catalog" cấu hình ở spring.grpc.client.channels.catalog. */
@Component
class CategoryGrpcClient implements CategoryCatalog {

    private final CategoryServiceGrpc.CategoryServiceBlockingStub stub;

    CategoryGrpcClient(GrpcChannelFactory channels) {
        this.stub = CategoryServiceGrpc.newBlockingStub(channels.createChannel("catalog"));
    }

    @Override
    @Retry(name = "catalog")
    @CircuitBreaker(name = "catalog")
    public CategoryRef byId(String categoryId) {
        return ref(call().getCategoryById(CategoryRequest.newBuilder().setCategoryId(categoryId).build()));
    }

    @Override
    @Retry(name = "catalog")
    @CircuitBreaker(name = "catalog")
    public CategoryRef bySlug(String slug) {
        return ref(call().getCategoryBySlug(CategorySlugRequest.newBuilder().setSlug(slug).build()));
    }

    @Override
    @Retry(name = "catalog")
    @CircuitBreaker(name = "catalog")
    public List<CategoryRef> byIds(List<String> categoryIds) {
        return call().getListCategoryByIds(ListCategoryRequest.newBuilder().addAllCategoryId(categoryIds).build())
                .getResponseList().stream().map(CategoryGrpcClient::ref).toList();
    }

    @Override
    @Retry(name = "catalog")
    @CircuitBreaker(name = "catalog")
    public void validate(List<String> categoryIds) {
        ValidateCategoriesResponse res = call().validateCategories(
                ValidateCategoriesRequest.newBuilder().addAllCategoryIds(categoryIds).build());
        if (!res.getValid()) {
            throw new IllegalArgumentException("Invalid categories: " + res.getInvalidCategoryIdsList());
        }
    }

    private CategoryServiceGrpc.CategoryServiceBlockingStub call() {
        return stub.withDeadlineAfter(2, TimeUnit.SECONDS);
    }

    private static CategoryRef ref(CategoryResponse c) {
        return new CategoryRef(c.getId(), c.getName(), c.getSlug());
    }
}
