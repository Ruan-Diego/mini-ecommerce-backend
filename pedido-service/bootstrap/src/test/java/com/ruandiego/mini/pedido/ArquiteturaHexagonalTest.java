package com.ruandiego.mini.pedido;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

/** Falha o build se alguém quebrar a direção das dependências da arquitetura hexagonal. */
@AnalyzeClasses(packages = "com.ruandiego.mini.pedido", importOptions = ImportOption.DoNotIncludeTests.class)
class ArquiteturaHexagonalTest {

    @ArchTest
    static final ArchRule dominioNaoConheceFrameworks = noClasses()
            .that().resideInAPackage("..domain..")
            .should().dependOnClassesThat().resideInAnyPackage(
                    "org.springframework..", "jakarta..", "com.fasterxml..", "org.hibernate..")
            .allowEmptyShould(true);

    @ArchTest
    static final ArchRule dominioNaoConheceCamadasExternas = noClasses()
            .that().resideInAPackage("..domain..")
            .should().dependOnClassesThat().resideInAnyPackage("..application..", "..adapters..")
            .allowEmptyShould(true);

    @ArchTest
    static final ArchRule applicationNaoConheceAdapters = noClasses()
            .that().resideInAPackage("..application..")
            .should().dependOnClassesThat().resideInAnyPackage("..adapters..", "org.springframework.web..", "jakarta.persistence..")
            .allowEmptyShould(true);
}
