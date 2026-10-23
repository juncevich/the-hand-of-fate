package com.juncevich.fate

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.modulith.core.ApplicationModules
import org.springframework.modulith.docs.Documenter

/**
 * `modules.verify()` only checks Spring Modulith's own encapsulation/cycle rules, which pass
 * as long as no code reaches into another module's sub-packages (only a module's base package is
 * its API). It does not assert what
 * the module set *should* be or which modules are allowed to depend on which — a module could
 * gain an unintended dependency (or vanish) and `verify()` alone would stay green. The tests
 * below pin down the architecture described in CLAUDE.md so a regression fails loudly here.
 */
class ModularityTest {
    private val modules = ApplicationModules.of(FateApplication::class.java)

    @Test
    fun `application modules are structurally valid`() {
        modules.verify()
    }

    @Test
    fun `module set matches the documented architecture`() {
        val actual = modules.map { it.identifier.toString() }.toSet()
        assertEquals(setOf("account", "vote", "shared", "grpc", "storage", "notification", "platform"), actual)
    }

    @Test
    fun `account module does not depend on vote or grpc`() {
        val account = modules.getModuleByName("account").orElseThrow()
        val dependencies = account.getDirectDependencies(modules)
        assertFalse(dependencies.containsModuleNamed("vote"))
        assertFalse(dependencies.containsModuleNamed("grpc"))
    }

    @Test
    fun `shared module does not depend on any other module`() {
        val shared = modules.getModuleByName("shared").orElseThrow()
        assertTrue(shared.getDirectDependencies(modules).isEmpty())
    }

    @Test
    fun `storage module depends only on shared`() {
        val storage = modules.getModuleByName("storage").orElseThrow()
        val dependencies = storage.getDirectDependencies(modules)
        listOf("account", "vote", "grpc", "platform").forEach { assertFalse(dependencies.containsModuleNamed(it)) }
    }

    @Test
    fun `notification module depends only on shared`() {
        val notification = modules.getModuleByName("notification").orElseThrow()
        val dependencies = notification.getDirectDependencies(modules)
        listOf("account", "vote", "grpc", "storage", "platform").forEach { assertFalse(dependencies.containsModuleNamed(it)) }
    }

    @Test
    fun `vote module does not depend on grpc`() {
        val vote = modules.getModuleByName("vote").orElseThrow()
        assertFalse(vote.getDirectDependencies(modules).containsModuleNamed("grpc"))
    }

    @Test
    fun `grpc module depends on both account and vote as the composition root`() {
        val grpc = modules.getModuleByName("grpc").orElseThrow()
        val dependencies = grpc.getDirectDependencies(modules)
        assertTrue(dependencies.containsModuleNamed("account"))
        assertTrue(dependencies.containsModuleNamed("vote"))
    }

    @Test
    fun `platform module depends only on shared`() {
        val platform = modules.getModuleByName("platform").orElseThrow()
        val dependencies = platform.getDirectDependencies(modules)
        listOf("account", "vote", "grpc", "storage", "notification").forEach {
            assertFalse(dependencies.containsModuleNamed(it))
        }
    }

    @Test
    fun `no module depends on platform`() {
        // Cross-cutting configuration (security, error mapping, OpenAPI, secrets) wires the
        // application; business modules must not build on it.
        modules
            .filter { it.identifier.toString() != "platform" }
            .forEach { assertFalse(it.getDirectDependencies(modules).containsModuleNamed("platform"), it.identifier.toString()) }
    }

    @Test
    fun `write module documentation`() {
        Documenter(modules)
            .writeModulesAsPlantUml()
            .writeIndividualModulesAsPlantUml()
    }

    @Test
    fun `other modules see accounts only as public profiles`() {
        com.tngtech.archunit.lang.syntax.ArchRuleDefinition
            .noClasses()
            .that()
            .resideOutsideOfPackage("com.juncevich.fate.account..")
            .should()
            .dependOnClassesThat()
            .haveFullyQualifiedName("com.juncevich.fate.account.domain.User")
            .check(
                com.tngtech.archunit.core.importer
                    .ClassFileImporter()
                    .withImportOption(com.tngtech.archunit.core.importer.ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
                    .importPackages("com.juncevich.fate")
            )
    }
}
