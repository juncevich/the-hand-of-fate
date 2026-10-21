package com.juncevich.fate.auth

import com.juncevich.fate.AbstractApiIntegrationTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.http.HttpMethod
import org.springframework.http.MediaType
import org.springframework.mock.web.MockMultipartFile
import org.springframework.test.web.servlet.delete
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.multipart
import java.awt.image.BufferedImage
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.UUID
import javax.imageio.ImageIO

class AvatarApiIntegrationTest : AbstractApiIntegrationTest() {
    private fun jpeg(): MockMultipartFile {
        val bytes = ByteArrayOutputStream().also { ImageIO.write(BufferedImage(800, 600, BufferedImage.TYPE_INT_RGB), "jpeg", it) }
        return MockMultipartFile("file", "photo.jpg", "image/jpeg", bytes.toByteArray())
    }

    private fun upload(
        token: String,
        file: MockMultipartFile = jpeg(),
    ) = mockMvc.multipart(HttpMethod.PUT, "/api/v1/users/me/avatar") {
        file(file)
        header("Authorization", "Bearer $token")
    }

    @Test
    fun `avatar lifecycle - upload, public download, cache validation, profile, delete`() {
        val email = "avatar-${UUID.randomUUID()}@test.com"
        val token = signUp(email)

        val avatarUrl =
            upload(token)
                .andExpect { status { isOk() } }
                .andReturn()
                .let { parse(it.response.contentAsString).text("avatarUrl") }
        assertTrue(avatarUrl.matches(Regex("/api/v1/users/[0-9a-f-]+/avatar\\?v=\\d+")), avatarUrl)

        // Public: no Authorization header; the stored object is the re-encoded 256px PNG
        val download =
            mockMvc
                .get(avatarUrl)
                .andExpect {
                    status { isOk() }
                    content { contentType(MediaType.IMAGE_PNG) }
                    header { string("Cache-Control", "max-age=31536000, public, immutable") }
                }.andReturn()
        val image = ImageIO.read(ByteArrayInputStream(download.response.contentAsByteArray))
        assertEquals(256, image.width)
        assertEquals(256, image.height)

        val etag = checkNotNull(download.response.getHeader("ETag"))
        mockMvc.get(avatarUrl) { header("If-None-Match", etag) }.andExpect { status { isNotModified() } }

        mockMvc
            .get("/api/v1/users/me") { header("Authorization", "Bearer $token") }
            .andExpect { jsonPath("$.avatarUrl") { value(avatarUrl) } }

        mockMvc
            .delete("/api/v1/users/me/avatar") { header("Authorization", "Bearer $token") }
            .andExpect { status { isNoContent() } }
        mockMvc.get(avatarUrl).andExpect { status { isNotFound() } }
    }

    @Test
    fun `new users have no avatar`() {
        val token = signUp("fresh-${UUID.randomUUID()}@test.com")
        val result =
            mockMvc
                .get("/api/v1/users/me") { header("Authorization", "Bearer $token") }
                .andExpect { status { isOk() } }
                .andReturn()
        val body = parse(result.response.contentAsString)
        val userId = body.text("userId")

        assertTrue(body["avatarUrl"].isNull)
        mockMvc.get("/api/v1/users/$userId/avatar").andExpect { status { isNotFound() } }
    }

    @Test
    fun `rejects non-image uploads`() {
        val token = signUp("avatar-bad-${UUID.randomUUID()}@test.com")

        upload(token, MockMultipartFile("file", "x.svg", "image/svg+xml", "<svg/>".toByteArray()))
            .andExpect {
                status { isBadRequest() }
                jsonPath("$.title") { value("Avatar must be a PNG, JPEG or GIF image") }
            }
    }

    @Test
    fun `changing the avatar requires authentication`() {
        mockMvc.multipart(HttpMethod.PUT, "/api/v1/users/me/avatar") { file(jpeg()) }.andExpect {
            status { isUnauthorized() }
        }
        mockMvc.delete("/api/v1/users/me/avatar").andExpect { status { isUnauthorized() } }
    }
}
