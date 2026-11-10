package com.congthanh.platform.temporal;

import io.temporal.worker.Worker;

import java.util.function.Consumer;

/**
 * Service khai báo bean kiểu này để đăng ký workflow/activity cho một task queue; auto-config tạo
 * worker và start/stop theo vòng đời Spring (graceful shutdown chờ activity đang chạy).
 */
public record TemporalWorkerRegistration(String taskQueue, Consumer<Worker> register) {
}
