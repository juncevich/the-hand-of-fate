package com.juncevich.fate

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.modulith.core.ApplicationModules
import org.springframework.modulith.docs.Documenter

/**
 * `modules.verify()` only checks Spring Modulith's own encapsulation/cycle rules, which pass
 * as long as no code reaches into another module's `internal` package. It does not assert what
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
        assertEquals(setOf("auth", "vote", "shared", "grpc"), actual)
    }

    @Test
    fun `auth module does not depend on vote or grpc`() {
        val auth = modules.getModuleByName("auth").orElseThrow()
        val dependencies = auth.getDirectDependencies(modules)
        assertFalse(dependencies.containsModuleNamed("vote"))
        assertFalse(dependencies.containsModuleNamed("grpc"))
    }

    @Test
    fun `shared module does not depend on any other module`() {
        val shared = modules.getModuleByName("shared").orElseThrow()
        assertTrue(shared.getDirectDependencies(modules).isEmpty())
    }

    @Test
    fun `vote module does not depend on grpc`() {
        val vote = modules.getModuleByName("vote").orElseThrow()
        assertFalse(vote.getDirectDependencies(modules).containsModuleNamed("grpc"))
    }

    @Test
    fun `grpc module depends on both auth and vote as the composition root`() {
        val grpc = modules.getModuleByName("grpc").orElseThrow()
        val dependencies = grpc.getDirectDependencies(modules)
        assertTrue(dependencies.containsModuleNamed("auth"))
        assertTrue(dependencies.containsModuleNamed("vote"))
    }

    @Test
    fun `write module documentation`() {
        Documenter(modules)
            .writeModulesAsPlantUml()
            .writeIndividualModulesAsPlantUml()
    }
}
