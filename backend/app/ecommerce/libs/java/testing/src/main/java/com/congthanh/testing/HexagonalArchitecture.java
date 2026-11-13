package com.congthanh.testing;

import com.tngtech.archunit.lang.ArchRule;

import java.util.ArrayList;
import java.util.List;

import static com.tngtech.archunit.core.domain.properties.CanBeAnnotated.Predicates.annotatedWith;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noMethods;
import static com.tngtech.archunit.library.dependencies.SlicesRuleDefinition.slices;

public final class HexagonalArchitecture {

    private HexagonalArchitecture() {
    }

    public static List<ArchRule> strict(String base) {
        List<ArchRule> rules = new ArrayList<>(common(base));
        rules.add(noClasses().that().resideInAPackage(base + ".domain..")
                .should().dependOnClassesThat().resideInAnyPackage(
                        "org.springframework..", "jakarta.persistence..", "io.grpc..", "io.temporal..",
                        "com.google.protobuf..", "tools.jackson..", "com.fasterxml.jackson..")
                .as("domain must be framework-free"));
        rules.add(noClasses().that().resideInAPackage(base + ".application..")
                .should().dependOnClassesThat().resideInAnyPackage(
                        "jakarta.persistence..", "io.grpc..", "io.temporal..", "org.springframework.web..")
                .as("application must not depend on transport or persistence technology"));
        return rules;
    }

    public static List<ArchRule> pragmatic(String base) {
        List<ArchRule> rules = new ArrayList<>(common(base));
        rules.add(noClasses().that().resideInAPackage(base + ".domain..")
                .should().dependOnClassesThat().resideInAnyPackage(
                        "org.springframework.web..", "io.grpc..", "org.springframework.graphql..")
                .as("domain must not depend on transports"));
        return rules;
    }

    private static List<ArchRule> common(String base) {
        return List.of(
                noClasses().that().resideInAPackage(base + ".domain..")
                        .should().dependOnClassesThat().resideInAnyPackage(
                                base + ".application..", base + ".adapter..", base + ".config..")
                        .as("domain depends on nothing else in the service"),
                noClasses().that().resideInAPackage(base + ".application..")
                        .should().dependOnClassesThat().resideInAnyPackage(base + ".adapter..", base + ".config..")
                        .as("application depends only on domain and its own ports"),
                slices().matching(base + ".adapter.(*).(*)..").should().notDependOnEachOther()
                        .as("adapters are independent of each other"),
                noMethods().that().haveNameMatching("equals|hashCode|toString")
                        .and().areDeclaredInClassesThat(annotatedWith("jakarta.persistence.Entity")
                                .or(annotatedWith("jakarta.persistence.MappedSuperclass"))
                                .or(annotatedWith("jakarta.persistence.Embeddable")))
                        .should().beAnnotatedWith("lombok.Generated")
                        .allowEmptyShould(true)
                        .as("JPA entities must not use Lombok-generated equals/hashCode/toString"),
                noClasses().that().resideInAPackage(base + ".adapter.in..")
                        .should().dependOnClassesThat().resideInAPackage(base + ".application.service..")
                        .as("inbound adapters call use-case ports, not service implementations"));
    }
}
