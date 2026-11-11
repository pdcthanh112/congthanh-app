package com.congthanh.productservice.adapter.in.web;

import com.congthanh.productservice.application.port.in.ProductReadModelUseCase;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

@RestController
@RequestMapping("/api/ecommerce/products/read-model")
class ProductReadModelController {

    private final ProductReadModelUseCase readModel;
    private final AtomicBoolean running = new AtomicBoolean();

    ProductReadModelController(ProductReadModelUseCase readModel) {
        this.readModel = readModel;
    }

    @PostMapping("/rebuild")
    ResponseEntity<Map<String, String>> rebuild() {
        if (!running.compareAndSet(false, true)) {
            return ResponseEntity.status(409).body(Map.of("status", "already running on this pod"));
        }
        Thread.ofVirtual().name("read-model-rebuild").start(() -> {
            try {
                readModel.rebuildAll();
            } finally {
                running.set(false);
            }
        });
        return ResponseEntity.accepted().body(Map.of("status", "started"));
    }
}
