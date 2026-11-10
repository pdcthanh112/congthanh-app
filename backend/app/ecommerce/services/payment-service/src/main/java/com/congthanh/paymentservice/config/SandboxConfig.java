package com.congthanh.paymentservice.config;

import com.congthanh.paymentservice.adapter.in.web.SandboxController;
import com.congthanh.paymentservice.adapter.out.provider.SandboxProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBooleanProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConditionalOnBooleanProperty("payment.sandbox.enabled")
class SandboxConfig {

    @Bean
    SandboxController.SandboxSigner sandboxSigner(SandboxProvider provider) {
        return provider::sign;
    }
}
