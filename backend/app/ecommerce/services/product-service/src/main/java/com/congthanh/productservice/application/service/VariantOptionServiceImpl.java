package com.congthanh.productservice.application.service;

import com.congthanh.commonservice.exception.BadRequestException;
import com.congthanh.commonservice.exception.ConflictException;
import com.congthanh.commonservice.exception.NotFoundException;
import com.congthanh.commonservice.id.Snowflake;
import com.congthanh.productservice.application.dto.request.CreateVariantRequest;
import com.congthanh.productservice.application.dto.viewmodel.ProductOptionCombinationGetVm;
import com.congthanh.productservice.application.dto.viewmodel.ProductOptionValueGetVm;
import com.congthanh.productservice.application.dto.viewmodel.ProductVariantVm;
import com.congthanh.productservice.application.dto.viewmodel.VariantOptionVm;
import com.congthanh.productservice.application.port.in.VariantOptionService;
import com.congthanh.productservice.application.port.out.ProductQueries;
import com.congthanh.productservice.application.port.out.ProductRepository;
import com.congthanh.productservice.application.port.out.VariantOptionRepository;
import com.congthanh.productservice.domain.model.Money;
import com.congthanh.productservice.domain.model.OptionSelection;
import com.congthanh.productservice.domain.model.OptionValue;
import com.congthanh.productservice.domain.model.Product;
import com.congthanh.productservice.domain.model.VariantOption;
import com.congthanh.productservice.domain.model.enums.ProductStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/** Trục variant dùng chung và tạo variant dưới sản phẩm gốc (ADR 0030). */
@Service
@RequiredArgsConstructor
public class VariantOptionServiceImpl implements VariantOptionService {

    private final ProductRepository products;
    private final ProductQueries queries;
    private final VariantOptionRepository options;

    @Override
    public List<VariantOptionVm> getOptions() {
        return options.findAll().stream().map(o -> new VariantOptionVm(o.id(), o.name())).toList();
    }

    @Override
    @Transactional
    public VariantOptionVm createOption(String name) {
        VariantOption option = new VariantOption(Snowflake.nextId(), name);
        if (options.findByNameIgnoreCase(option.name()).isPresent()) {
            throw new ConflictException("option already exists: " + option.name());
        }
        options.save(option);
        return new VariantOptionVm(option.id(), option.name());
    }

    @Override
    public List<ProductOptionValueGetVm> getVariantOptionValueByProduct(String productId) {
        requireProduct(productId);
        return queries.optionValues(productId);
    }

    @Override
    public List<ProductOptionCombinationGetVm> getVariantOptionCombinationByProduct(String productId) {
        requireProduct(productId);
        return queries.usedOptionValues(productId);
    }

    @Override
    @Transactional
    public ProductVariantVm createVariant(String parentId, CreateVariantRequest request) {
        Product parent = requireProduct(parentId);
        String sku = request.sku().strip();
        if (products.existsSkuOnOther(sku, null)) {
            throw new ConflictException("sku already exists: " + sku);
        }
        String gtin = request.gtin() == null || request.gtin().isBlank() ? null : request.gtin().strip();
        if (gtin != null && products.existsGtinOnOther(gtin, null)) {
            throw new ConflictException("gtin already exists: " + gtin);
        }
        List<OptionValue> chosen = new ArrayList<>(request.options().size());
        for (CreateVariantRequest.OptionSelection s : request.options()) {
            VariantOption option = options.findById(s.optionId())
                    .orElseThrow(() -> new NotFoundException("option not found: " + s.optionId()));
            chosen.add(parent.declareOptionValue(option.id(), s.value(), Snowflake::nextId));
        }
        String key = Product.variantKey(chosen);
        if (products.existsLiveVariantKey(parent.id(), key)) {
            throw new ConflictException("a variant with these options already exists");
        }
        Product variant = parent.createVariant(
                new Product.VariantDraft(Snowflake.nextString(), request.name(), sku, gtin,
                        Money.ofNullable(request.price(), request.currency()), status(request.status())),
                chosen, existingAxes(parent.id()));

        products.save(parent);
        products.save(variant);

        Map<Long, String> valueById = chosen.stream().collect(Collectors.toMap(OptionValue::id, OptionValue::value));
        Map<Long, String> selected = new LinkedHashMap<>();
        for (OptionSelection s : variant.selections()) {
            selected.put(s.optionId(), valueById.get(s.optionValueId()));
        }
        return new ProductVariantVm(variant.id(), variant.name(), null, variant.sku(), variant.gtin(),
                variant.price() == null ? null : variant.price().amount(),
                variant.price() == null ? null : variant.price().currency(), null, List.of(), selected);
    }

    /** Bộ trục của một variant chưa xoá bất kỳ (mọi variant dùng cùng bộ trục); rỗng nếu chưa có variant. */
    private Set<Long> existingAxes(String parentId) {
        return products.findVariants(parentId).stream()
                .filter(v -> v.status() != ProductStatus.DELETED)
                .findFirst()
                .map(v -> v.selections().stream().map(OptionSelection::optionId).collect(Collectors.toSet()))
                .orElse(Set.of());
    }

    private Product requireProduct(String id) {
        return products.findById(id)
                .filter(p -> p.status() != ProductStatus.DELETED)
                .orElseThrow(() -> new NotFoundException("product not found: " + id));
    }

    private static ProductStatus status(String raw) {
        if (raw == null) {
            return null;
        }
        try {
            return ProductStatus.valueOf(raw.strip().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new BadRequestException("status must be ACTIVE or INACTIVE");
        }
    }
}
