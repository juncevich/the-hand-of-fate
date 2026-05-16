package com.juncevich.fate

import org.junit.jupiter.api.Test
import org.springframework.modulith.core.ApplicationModules
import org.springframework.modulith.docs.Documenter

class ModularityTest {
    private val modules = ApplicationModules.of(FateApplication::class.java)

    @Test
    fun `application modules are structurally valid`() {
        modules.verify()
    }

    @Test
    fun `write module documentation`() {
        Documenter(modules)
            .writeModulesAsPlantUml()
            .writeIndividualModulesAsPlantUml()
    }
}
