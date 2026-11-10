package com.congthanh.promotionservice;

import com.congthanh.testing.HexagonalArchitecture;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import org.junit.jupiter.api.Test;

class ArchitectureTest {

    private static final String BASE = "com.congthanh.promotionservice";

    @Test
    void followsHexagonalRules() {
        JavaClasses classes = new ClassFileImporter()
                .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
                .importPackages(BASE);
        HexagonalArchitecture.strict(BASE).forEach(rule -> rule.check(classes));
    }
}
