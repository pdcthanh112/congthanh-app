package com.congthanh.platform.migration;

import liquibase.integration.spring.SpringLiquibase;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.jdbc.core.simple.JdbcClient;

import java.util.LinkedHashSet;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Liquibase không tự tạo schema. Mỗi service sở hữu một schema riêng ({@code spring.liquibase.default-schema}),
 * nên trước khi chạy changelog ta tạo schema đó (và schema chứa bảng DATABASECHANGELOG nếu khác) nếu chưa có.
 */
public class LiquibaseSchemaCreator implements BeanPostProcessor {

    private static final Pattern IDENTIFIER = Pattern.compile("[a-z_][a-z0-9_]{0,62}");

    @Override
    public Object postProcessBeforeInitialization(Object bean, String beanName) {
        if (bean instanceof SpringLiquibase liquibase && liquibase.getDataSource() != null) {
            Set<String> schemas = new LinkedHashSet<>();
            if (liquibase.getDefaultSchema() != null) schemas.add(liquibase.getDefaultSchema());
            if (liquibase.getLiquibaseSchema() != null) schemas.add(liquibase.getLiquibaseSchema());
            JdbcClient jdbc = JdbcClient.create(liquibase.getDataSource());
            for (String schema : schemas) {
                if (!IDENTIFIER.matcher(schema).matches()) {
                    throw new IllegalStateException("Invalid schema name for Liquibase: " + schema);
                }
                jdbc.sql("CREATE SCHEMA IF NOT EXISTS " + schema).update();
            }
        }
        return bean;
    }
}
