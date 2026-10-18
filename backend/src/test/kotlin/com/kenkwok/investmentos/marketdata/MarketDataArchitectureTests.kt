package com.kenkwok.investmentos.marketdata

import com.tngtech.archunit.core.importer.ClassFileImporter
import com.tngtech.archunit.core.importer.ImportOption
import com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes
import com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses
import org.junit.jupiter.api.Test

class MarketDataArchitectureTests {
    @Test
    fun `GIVEN market data classes WHEN checking dependencies THEN domain and application remain infrastructure independent`() {
        // GIVEN
        val importedClasses =
            ClassFileImporter()
                .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
                .importPackages("com.kenkwok.investmentos.marketdata")
        val domain = "com.kenkwok.investmentos.marketdata.domain.."
        val application = "com.kenkwok.investmentos.marketdata.application.."
        val adapter = "com.kenkwok.investmentos.marketdata.adapter.."
        val configuration = "com.kenkwok.investmentos.marketdata.configuration.."
        val rules =
            listOf(
                classes()
                    .that()
                    .resideInAPackage(domain)
                    .should()
                    .onlyDependOnClassesThat()
                    .resideInAnyPackage(domain, "java..", "kotlin..", "org.jetbrains.annotations.."),
                classes()
                    .that()
                    .resideInAPackage(application)
                    .should()
                    .onlyDependOnClassesThat()
                    .resideInAnyPackage(application, domain, "java..", "kotlin..", "org.jetbrains.annotations.."),
                noClasses()
                    .that()
                    .resideInAnyPackage(domain, application)
                    .should()
                    .dependOnClassesThat()
                    .resideInAnyPackage(
                        "org.springframework..",
                        "tools.jackson..",
                        "com.fasterxml.jackson..",
                        "jakarta.persistence..",
                        "java.net..",
                        "java.sql..",
                        "javax.sql..",
                        "org.apache.hc..",
                    ),
                noClasses()
                    .that()
                    .resideInAPackage(adapter)
                    .should()
                    .dependOnClassesThat()
                    .resideInAPackage(configuration),
            )

        // WHEN
        val evaluations = rules.map { it.evaluate(importedClasses) }

        // THEN
        evaluations.forEach { result -> kotlin.test.assertFalse(result.hasViolation(), result.failureReport.toString()) }
    }
}
