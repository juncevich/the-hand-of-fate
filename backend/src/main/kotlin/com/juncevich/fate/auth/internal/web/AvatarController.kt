package com.juncevich.fate.auth.internal.web

import com.juncevich.fate.auth.AuthenticatedUser
import com.juncevich.fate.auth.internal.service.AvatarService
import org.springframework.http.CacheControl
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.*
import org.springframework.web.context.request.WebRequest
import org.springframework.web.multipart.MultipartFile
import java.time.Duration
import java.util.UUID

data class AvatarResponse(
    val avatarUrl: String,
)

@RestController
@RequestMapping("/api/v1/users")
class AvatarController(
    private val avatarService: AvatarService,
) {
    @PutMapping("/me/avatar", consumes = [MediaType.MULTIPART_FORM_DATA_VALUE])
    fun upload(
        @AuthenticationPrincipal user: AuthenticatedUser,
        @RequestParam("file") file: MultipartFile,
    ): AvatarResponse = AvatarResponse(avatarService.upload(user.id, file.bytes))

    @DeleteMapping("/me/avatar")
    fun delete(
        @AuthenticationPrincipal user: AuthenticatedUser,
    ): ResponseEntity<Unit> {
        avatarService.delete(user.id)
        return ResponseEntity.noContent().build()
    }

    /**
     * Public so it can back a plain `<img src>` (the SPA keeps its access token in memory and
     * cannot attach it to image requests). A request carrying the current `?v=` is immutable and
     * cached for a year; any other one is revalidated against the ETag.
     */
    @GetMapping("/{userId}/avatar")
    fun get(
        @PathVariable userId: UUID,
        @RequestParam("v", required = false) version: Long?,
        request: WebRequest,
    ): ResponseEntity<ByteArray> {
        val avatar = avatarService.load(userId)
        val etag = "\"${avatar.version}\""
        val cacheControl =
            if (version == avatar.version) {
                CacheControl.maxAge(Duration.ofDays(CACHE_DAYS)).cachePublic().immutable()
            } else {
                CacheControl.noCache()
            }
        if (request.checkNotModified(etag)) {
            return ResponseEntity
                .status(HttpStatus.NOT_MODIFIED)
                .eTag(etag)
                .cacheControl(cacheControl)
                .build()
        }
        return ResponseEntity
            .ok()
            .eTag(etag)
            .cacheControl(cacheControl)
            .contentType(MediaType.parseMediaType(avatar.contentType))
            .body(avatar.content)
    }

    private companion object {
        const val CACHE_DAYS = 365L
    }
}
