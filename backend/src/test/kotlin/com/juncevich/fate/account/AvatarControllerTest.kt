package com.juncevich.fate.account

import com.juncevich.fate.account.avatar.Avatar
import com.juncevich.fate.account.avatar.AvatarService
import com.juncevich.fate.account.avatar.InvalidAvatarException
import com.juncevich.fate.account.web.AvatarController
import com.juncevich.fate.platform.ErrorHandler
import com.juncevich.fate.shared.Email
import com.juncevich.fate.shared.ErrorCode
import com.juncevich.fate.shared.NotFoundException
import com.juncevich.fate.shared.UserId
import com.juncevich.fate.storage.StorageUnavailableException
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.runs
import io.mockk.verify
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.http.HttpMethod
import org.springframework.mock.web.MockMultipartFile
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.security.web.method.annotation.AuthenticationPrincipalArgumentResolver
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.delete
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.multipart
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import java.util.UUID

class AvatarControllerTest {
    private val avatarService = mockk<AvatarService>()
    private lateinit var mockMvc: MockMvc

    private val userId = UserId(UUID.randomUUID())
    private val file = MockMultipartFile("file", "me.png", "image/png", byteArrayOf(1, 2, 3))

    @BeforeEach
    fun setUp() {
        mockMvc =
            MockMvcBuilders
                .standaloneSetup(AvatarController(avatarService))
                .setControllerAdvice(ErrorHandler())
                .setCustomArgumentResolvers(AuthenticationPrincipalArgumentResolver())
                .build()

        SecurityContextHolder.getContext().authentication =
            UsernamePasswordAuthenticationToken(
                AuthenticatedUser(userId, Email.of("user@test.com")),
                null,
                listOf(SimpleGrantedAuthority("ROLE_USER"))
            )
    }

    @AfterEach
    fun tearDown() {
        SecurityContextHolder.clearContext()
    }

    @Test
    fun `PUT avatar - stores the upload and returns the new url`() {
        every { avatarService.upload(userId, byteArrayOf(1, 2, 3)) } returns "/api/v1/users/$userId/avatar?v=1"

        mockMvc.multipart(HttpMethod.PUT, "/api/v1/users/me/avatar") { file(file) }.andExpect {
            status { isOk() }
            jsonPath("$.avatarUrl") { value("/api/v1/users/$userId/avatar?v=1") }
        }
    }

    @Test
    fun `PUT avatar - invalid image is a 400`() {
        every { avatarService.upload(userId, any()) } throws InvalidAvatarException("Avatar must be a PNG, JPEG or GIF image")

        mockMvc.multipart(HttpMethod.PUT, "/api/v1/users/me/avatar") { file(file) }.andExpect {
            status { isBadRequest() }
            jsonPath("$.title") { value("Avatar must be a PNG, JPEG or GIF image") }
        }
    }

    @Test
    fun `PUT avatar - missing file part is a 400`() {
        mockMvc.multipart(HttpMethod.PUT, "/api/v1/users/me/avatar").andExpect {
            status { isBadRequest() }
        }
    }

    @Test
    fun `PUT avatar - unconfigured storage is a 409`() {
        every { avatarService.upload(userId, any()) } throws StorageUnavailableException()

        mockMvc.multipart(HttpMethod.PUT, "/api/v1/users/me/avatar") { file(file) }.andExpect {
            status { isConflict() }
            jsonPath("$.title") { value("File storage is not configured") }
        }
    }

    @Test
    fun `DELETE avatar - returns 204`() {
        every { avatarService.delete(userId) } just runs

        mockMvc.delete("/api/v1/users/me/avatar").andExpect { status { isNoContent() } }
        verify { avatarService.delete(userId) }
    }

    @Test
    fun `GET avatar - current version is served as immutable`() {
        every { avatarService.load(userId) } returns Avatar(byteArrayOf(7), "image/png", 5)

        mockMvc.get("/api/v1/users/$userId/avatar?v=5").andExpect {
            status { isOk() }
            content { contentType("image/png") }
            content { bytes(byteArrayOf(7)) }
            header { string("ETag", "\"5\"") }
            header { string("Cache-Control", "max-age=31536000, public, immutable") }
        }
    }

    @Test
    fun `GET avatar - unversioned request must revalidate`() {
        every { avatarService.load(userId) } returns Avatar(byteArrayOf(7), "image/png", 5)

        mockMvc.get("/api/v1/users/$userId/avatar").andExpect {
            status { isOk() }
            header { string("Cache-Control", "no-cache") }
        }
        mockMvc.get("/api/v1/users/$userId/avatar?v=4").andExpect {
            header { string("Cache-Control", "no-cache") }
        }
    }

    @Test
    fun `GET avatar - matching ETag is a 304 without body`() {
        every { avatarService.load(userId) } returns Avatar(byteArrayOf(7), "image/png", 5)

        mockMvc
            .get("/api/v1/users/$userId/avatar") { header("If-None-Match", "\"5\"") }
            .andExpect {
                status { isNotModified() }
                header { string("ETag", "\"5\"") }
                content { bytes(ByteArray(0)) }
            }
    }

    @Test
    fun `GET avatar - missing avatar is a 404`() {
        every { avatarService.load(userId) } throws NotFoundException("Avatar not found", ErrorCode.VOTE_NOT_FOUND)

        mockMvc.get("/api/v1/users/$userId/avatar").andExpect {
            status { isNotFound() }
            jsonPath("$.title") { value("Avatar not found") }
        }
    }
}
