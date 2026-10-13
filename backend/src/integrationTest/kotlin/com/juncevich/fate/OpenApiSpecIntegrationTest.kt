package com.juncevich.fate

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.springframework.test.web.servlet.get
import java.nio.file.Files
import java.nio.file.Path

/**
 * Keeps the committed REST contract (`api/openapi.yaml`, consumed by the frontend and the
 * simulation clients) in sync with the controllers. Regenerate it with `./gradlew openApiUpdate`.
 */
class OpenApiSpecIntegrationTest : AbstractApiIntegrationTest() {
    @Test
    fun `committed OpenAPI spec matches the controllers`() {
        val actual =
            mockMvc
                .get("/v3/api-docs.yaml")
                .andExpect { status { isOk() } }
                .andReturn()
                .response
                .getContentAsString(Charsets.UTF_8)
        val specFile = Path.of(System.getProperty(SPEC_PATH_PROPERTY, DEFAULT_SPEC_PATH))

        if (System.getProperty(UPDATE_PROPERTY).toBoolean()) {
            Files.createDirectories(specFile.toAbsolutePath().parent)
            Files.writeString(specFile, actual)
            return
        }

        val committed = if (Files.exists(specFile)) Files.readString(specFile) else ""
        assertEquals(
            committed,
            actual,
            "$specFile is out of date with the REST controllers; run `./gradlew openApiUpdate` and commit the result"
        )
    }

    private companion object {
        const val SPEC_PATH_PROPERTY = "openapi.spec"
        const val UPDATE_PROPERTY = "openapi.update"
        const val DEFAULT_SPEC_PATH = "../api/openapi.yaml"
    }
}
