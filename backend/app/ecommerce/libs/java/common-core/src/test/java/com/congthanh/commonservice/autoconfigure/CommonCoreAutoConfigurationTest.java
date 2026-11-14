package com.congthanh.commonservice.autoconfigure;

import com.congthanh.commonservice.grpc.DomainGrpcExceptionHandler;
import com.congthanh.commonservice.web.CommonProblemHandler;
import com.congthanh.commonservice.web.PersistenceProblemHandler;
import com.congthanh.commonservice.web.Problems;
import com.congthanh.commonservice.web.ResilienceProblemHandler;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.boot.test.context.runner.WebApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;

import static org.assertj.core.api.Assertions.assertThat;

class CommonCoreAutoConfigurationTest {

    private final WebApplicationContextRunner web = new WebApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(CommonCoreAutoConfiguration.class));

    @Test
    void webServiceGetsErrorHandlingAndAccessLogButNoCors() {
        web.run(context -> {
            assertThat(context).hasSingleBean(CommonProblemHandler.class);
            assertThat(context).hasSingleBean(ResilienceProblemHandler.class);
            assertThat(context).hasSingleBean(PersistenceProblemHandler.class);
            assertThat(context).hasSingleBean(DomainGrpcExceptionHandler.class);
            assertThat(context).hasBean("requestLoggingFilter");
            assertThat(context).doesNotHaveBean("commonCorsFilter");
        });
    }

    @Test
    void corsTurnsOnWhenOriginsAreConfigured() {
        web.withPropertyValues("common.cors.allowed-origins=https://shop.example.com")
                .run(context -> assertThat(context).hasBean("commonCorsFilter"));
    }

    @Test
    void accessLogCanBeTurnedOff() {
        web.withPropertyValues("common.logging.requests-enabled=false")
                .run(context -> assertThat(context).doesNotHaveBean("requestLoggingFilter"));
    }

    @Test
    void problemTypeBaseIsConfigurable() {
        web.withPropertyValues("common.problems.type-base=https://errors.example.com/")
                .run(context -> assertThat(context.getBean(Problems.class)
                        .of(HttpStatus.CONFLICT, "PRICE_CHANGED", "x").getType())
                        .hasToString("https://errors.example.com/price-changed"));
    }

    @Test
    void serviceCanReplaceTheCommonHandler() {
        web.withUserConfiguration(OwnHandler.class).run(context -> {
            assertThat(context).hasSingleBean(CommonProblemHandler.class);
            assertThat(context).hasBean("ownHandler");
        });
    }

    @Test
    void nonWebApplicationGetsNoServletBeans() {
        new ApplicationContextRunner().withConfiguration(AutoConfigurations.of(CommonCoreAutoConfiguration.class))
                .run(context -> {
                    assertThat(context).doesNotHaveBean(CommonProblemHandler.class);
                    assertThat(context).doesNotHaveBean("requestLoggingFilter");
                    assertThat(context).hasSingleBean(Problems.class);
                });
    }

    @Configuration(proxyBeanMethods = false)
    static class OwnHandler {
        @Bean
        CommonProblemHandler ownHandler(Problems problems) {
            return new CommonProblemHandler(problems);
        }
    }
}
