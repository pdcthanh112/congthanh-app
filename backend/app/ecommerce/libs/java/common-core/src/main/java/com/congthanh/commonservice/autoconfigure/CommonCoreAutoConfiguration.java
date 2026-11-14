package com.congthanh.commonservice.autoconfigure;

import com.congthanh.commonservice.grpc.DomainGrpcExceptionHandler;
import com.congthanh.commonservice.persistence.SecurityAuditorAware;
import com.congthanh.commonservice.web.CommonProblemHandler;
import com.congthanh.commonservice.web.PersistenceProblemHandler;
import com.congthanh.commonservice.web.Problems;
import com.congthanh.commonservice.web.RequestLoggingFilter;
import com.congthanh.commonservice.web.ResilienceProblemHandler;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBooleanProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.hibernate.autoconfigure.HibernatePropertiesCustomizer;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.data.domain.AuditorAware;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.web.filter.CorsFilter;

@AutoConfiguration(afterName = {
        "org.springframework.boot.data.jpa.autoconfigure.DataJpaRepositoriesAutoConfiguration",
        "org.springframework.boot.hibernate.autoconfigure.HibernateJpaAutoConfiguration"})
@EnableConfigurationProperties(CommonCoreProperties.class)
public class CommonCoreAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    Problems problems(CommonCoreProperties props) {
        return new Problems(props.problems().typeBase());
    }

    @Configuration(proxyBeanMethods = false)
    @ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
    static class WebConfiguration {

        @Bean
        @ConditionalOnMissingBean
        CommonProblemHandler commonProblemHandler(Problems problems) {
            return new CommonProblemHandler(problems);
        }

        @Bean
        @ConditionalOnBooleanProperty(name = "common.logging.requests-enabled", matchIfMissing = true)
        FilterRegistrationBean<RequestLoggingFilter> requestLoggingFilter(CommonCoreProperties props) {
            var registration = new FilterRegistrationBean<>(new RequestLoggingFilter(props.logging().excludePaths()));
            registration.setOrder(-110);
            return registration;
        }

        @Bean
        @ConditionalOnProperty(name = "common.cors.allowed-origins")
        FilterRegistrationBean<CorsFilter> commonCorsFilter(CommonCoreProperties props) {
            CommonCoreProperties.Cors cors = props.cors();
            CorsConfiguration config = new CorsConfiguration();
            config.setAllowedOriginPatterns(cors.allowedOrigins());
            config.setAllowedMethods(cors.allowedMethods());
            config.setAllowedHeaders(cors.allowedHeaders());
            config.setExposedHeaders(cors.exposedHeaders());
            config.setAllowCredentials(cors.allowCredentials());
            config.setMaxAge(cors.maxAge());
            UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
            source.registerCorsConfiguration("/**", config);
            var registration = new FilterRegistrationBean<>(new CorsFilter(source));
            registration.setOrder(Ordered.HIGHEST_PRECEDENCE + 10);
            return registration;
        }
    }

    @Configuration(proxyBeanMethods = false)
    @ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
    @ConditionalOnClass(name = "io.github.resilience4j.circuitbreaker.CallNotPermittedException")
    static class ResilienceWebConfiguration {

        @Bean
        @ConditionalOnMissingBean
        ResilienceProblemHandler resilienceProblemHandler(Problems problems) {
            return new ResilienceProblemHandler(problems);
        }
    }

    @Configuration(proxyBeanMethods = false)
    @ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
    @ConditionalOnClass(name = "org.springframework.dao.OptimisticLockingFailureException")
    static class PersistenceWebConfiguration {

        @Bean
        @ConditionalOnMissingBean
        PersistenceProblemHandler persistenceProblemHandler(Problems problems) {
            return new PersistenceProblemHandler(problems);
        }
    }

    @Configuration(proxyBeanMethods = false)
    @ConditionalOnClass(name = "org.springframework.grpc.server.exception.GrpcExceptionHandler")
    static class GrpcConfiguration {

        @Bean
        @ConditionalOnMissingBean
        DomainGrpcExceptionHandler domainGrpcExceptionHandler() {
            return new DomainGrpcExceptionHandler();
        }
    }

    @Configuration(proxyBeanMethods = false)
    @ConditionalOnClass(name = "org.springframework.data.jpa.repository.config.EnableJpaAuditing")
    @ConditionalOnBean(name = "jpaMappingContext")
    static class AuditingConfiguration {

        @Bean
        @ConditionalOnMissingBean(AuditorAware.class)
        SecurityAuditorAware auditorAware(CommonCoreProperties props) {
            return new SecurityAuditorAware(props.audit().systemAuditor());
        }

        @Configuration(proxyBeanMethods = false)
        @ConditionalOnMissingBean(name = "jpaAuditingHandler")
        @EnableJpaAuditing
        static class EnableAuditing {
        }
    }

    @Configuration(proxyBeanMethods = false)
    @ConditionalOnClass(name = "org.springframework.boot.hibernate.autoconfigure.HibernatePropertiesCustomizer")
    static class SlowQueryConfiguration {

        @Bean
        HibernatePropertiesCustomizer slowQueryLogCustomizer(CommonCoreProperties props) {
            long thresholdMs = props.logging().slowQueryThreshold().toMillis();
            return hibernate -> {
                if (thresholdMs > 0) {
                    hibernate.putIfAbsent("hibernate.log_slow_query", thresholdMs);
                }
            };
        }
    }
}
