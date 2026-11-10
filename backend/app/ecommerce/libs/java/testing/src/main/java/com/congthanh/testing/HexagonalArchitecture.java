package com.congthanh.testing;

import com.tngtech.archunit.lang.ArchRule;

import java.util.ArrayList;
import java.util.List;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.library.dependencies.SlicesRuleDefinition.slices;

/**
 * Luật hexagonal dùng chung cho service Java. Cấu trúc gói:
 * <pre>
 * &lt;base&gt;.domain        model + quy tắc nghiệp vụ, không phụ thuộc framework
 * &lt;base&gt;.application   use case (port.in), port ra ngoài (port.out), service điều phối
 * &lt;base&gt;.adapter.in.*  web, grpc, graphql, messaging, workflow: gọi port.in
 * &lt;base&gt;.adapter.out.* persistence, grpc client, payment provider...: hiện thực port.out
 * &lt;base&gt;.config        lắp ráp (Spring @Configuration)
 * </pre>
 */
public final class HexagonalArchitecture {

    private HexagonalArchitecture() {
    }

    /** Domain thuần Java: không Spring, không JPA, không gRPC/Temporal. */
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

    /**
     * Cho service còn dùng JPA entity làm domain model (product-service): domain được phép
     * dùng annotation JPA, nhưng vẫn không được biết tới adapter hay web/gRPC.
     */
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
                noClasses().that().resideInAPackage(base + ".adapter.in..")
                        .should().dependOnClassesThat().resideInAPackage(base + ".application.service..")
                        .as("inbound adapters call use-case ports, not service implementations"));
    }
}
