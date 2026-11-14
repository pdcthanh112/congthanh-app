package com.congthanh.commonservice.persistence;

import com.congthanh.commonservice.autoconfigure.CommonCoreAutoConfiguration;
import com.congthanh.commonservice.model.BaseEntity;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.data.jpa.autoconfigure.DataJpaRepositoriesAutoConfiguration;
import org.springframework.boot.hibernate.autoconfigure.HibernateJpaAutoConfiguration;
import org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration;
import org.springframework.boot.persistence.autoconfigure.EntityScan;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.boot.transaction.autoconfigure.TransactionAutoConfiguration;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import static org.assertj.core.api.Assertions.assertThat;

class AuditingTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(DataSourceAutoConfiguration.class, HibernateJpaAutoConfiguration.class,
                    TransactionAutoConfiguration.class, DataJpaRepositoriesAutoConfiguration.class,
                    CommonCoreAutoConfiguration.class))
            .withUserConfiguration(JpaApp.class)
            .withPropertyValues("spring.datasource.url=jdbc:h2:mem:audit;DB_CLOSE_DELAY=-1",
                    "spring.jpa.hibernate.ddl-auto=create-drop");

    @AfterEach
    void clear() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void stampsTheLoggedInUser() {
        runner.run(context -> {
            SecurityContextHolder.getContext().setAuthentication(new TestingAuthenticationToken("seller-9", "n/a", "ROLE_SELLER"));

            Note saved = context.getBean(NoteRepository.class).saveAndFlush(new Note());

            assertThat(saved.getCreatedBy()).isEqualTo("seller-9");
            assertThat(saved.getUpdatedBy()).isEqualTo("seller-9");
            assertThat(saved.getCreatedAt()).isNotNull();
        });
    }

    @Test
    void fallsBackToTheSystemAuditorWithoutAUser() {
        runner.withPropertyValues("common.audit.system-auditor=catalog-job").run(context -> {
            Note saved = context.getBean(NoteRepository.class).saveAndFlush(new Note());

            assertThat(saved.getCreatedBy()).isEqualTo("catalog-job");
        });
    }

    @Configuration(proxyBeanMethods = false)
    @EntityScan(basePackageClasses = AuditingTest.class)
    @EnableJpaRepositories(basePackageClasses = AuditingTest.class, considerNestedRepositories = true)
    static class JpaApp {
    }

    interface NoteRepository extends JpaRepository<Note, Long> {
    }

    @Entity
    static class Note extends BaseEntity {
        @Id
        @GeneratedValue
        Long id;
    }
}
