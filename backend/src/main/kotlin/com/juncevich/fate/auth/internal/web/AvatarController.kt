package com.juncevich.fate.auth.internal.web

import com.juncevich.fate.auth.AuthenticatedUser
import com.juncevich.fate.auth.internal.service.AvatarService
import com.juncevich.fate.shared.OpenApiDocs
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.headers.Header
import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.security.SecurityRequirements
import io.swagger.v3.oas.annotations.tags.Tag
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
@Tag(name = "Avatars", description = "User avatar images")
class AvatarController(
    private val avatarService: AvatarService,
) {
    @PutMapping("/me/avatar", consumes = [MediaType.MULTIPART_FORM_DATA_VALUE])
    @Operation(
        operationId = "uploadAvatar",
        summary = "Upload or replace the current user's avatar",
        description =
            "PNG, JPEG or GIF up to 2 MB; stored center-cropped as a 256x256 PNG. " +
                "409 when object storage is disabled."
    )
    fun upload(
        @AuthenticationPrincipal user: AuthenticatedUser,
        @RequestParam("file") file: MultipartFile,
    ): AvatarResponse = AvatarResponse(avatarService.upload(user.id, file.bytes))

    @DeleteMapping("/me/avatar")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(operationId = "deleteAvatar", summary = "Delete the current user's avatar")
    fun delete(
        @AuthenticationPrincipal user: AuthenticatedUser,
    ) {
        avatarService.delete(user.id)
    }

    /**
     * Public so it can back a plain `<img src>` (the SPA keeps its access token in memory and
     * cannot attach it to image requests). A request carrying the current `?v=` is immutable and
     * cached for a year; any other one is revalidated against the ETag.
     */
    @GetMapping("/{userId}/avatar")
    @Operation(operationId = "getAvatar", summary = "Avatar image of a user")
    @SecurityRequirements
    @ApiResponse(
        responseCode = "200",
        description = "The avatar image",
        headers = [
            Header(name = "ETag", schema = Schema(type = "string")),
            Header(name = "Cache-Control", schema = Schema(type = "string"))
        ],
        content = [Content(mediaType = MediaType.IMAGE_PNG_VALUE, schema = Schema(type = "string", format = "binary"))]
    )
    @ApiResponse(responseCode = "304", description = "Not modified since the ETag in If-None-Match")
    @ApiResponse(
        responseCode = "404",
        description = "The user has no avatar",
        content = [Content(mediaType = OpenApiDocs.PROBLEM_MEDIA_TYPE, schema = Schema(ref = OpenApiDocs.PROBLEM_REF))]
    )
    fun get(
        @PathVariable userId: UUID,
        @Parameter(description = "Avatar version from `avatarUrl`; a matching value makes the response immutable")
        @RequestParam("v", required = false)
        version: Long?,
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
