package com.congthanh.productservice.adapter.in.web;

import com.congthanh.productservice.application.port.in.ProductEventReplayUseCase;
import com.congthanh.productservice.application.port.in.ProductReadModelUseCase;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

@RestController
@RequestMapping("/api/ecommerce/products/read-model")
@RequiredArgsConstructor
class ProductReadModelController {

    private final ProductReadModelUseCase readModel;
    private final ProductEventReplayUseCase replay;
    private final AtomicBoolean running = new AtomicBoolean();
    private final AtomicBoolean replaying = new AtomicBoolean();

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

    @PostMapping("/replay-events")
    ResponseEntity<Map<String, String>> replayEvents() {
        if (!replaying.compareAndSet(false, true)) {
            return ResponseEntity.status(409).body(Map.of("status", "already running on this pod"));
        }
        Thread.ofVirtual().name("product-event-replay").start(() -> {
            try {
                replay.replayAll();
            } finally {
                replaying.set(false);
            }
        });
        return ResponseEntity.accepted().body(Map.of("status", "started"));
    }
}
