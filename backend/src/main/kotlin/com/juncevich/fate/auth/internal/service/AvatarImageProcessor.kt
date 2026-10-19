package com.juncevich.fate.auth.internal.service

import com.juncevich.fate.shared.BadRequestException
import com.juncevich.fate.shared.ErrorCode
import org.springframework.stereotype.Component
import java.awt.RenderingHints
import java.awt.image.BufferedImage
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.IOException
import javax.imageio.ImageIO
import javax.imageio.ImageReader
import javax.imageio.stream.MemoryCacheImageInputStream

/** The upload is not a usable image. */
class InvalidAvatarException(
    message: String,
) : BadRequestException(message, ErrorCode.AVATAR_INVALID)

/**
 * Turns an uploaded image into the stored avatar: a center-cropped [SIZE]×[SIZE] PNG.
 * Re-encoding (rather than storing the upload) drops EXIF metadata such as GPS coordinates
 * and guarantees that only a well-formed PNG is ever served back.
 */
@Component
class AvatarImageProcessor {
    fun toAvatarPng(upload: ByteArray): ByteArray {
        if (upload.isEmpty()) throw InvalidAvatarException("Avatar file is empty")
        val square = cropToSquare(decode(upload))
        val avatar = resize(square, SIZE)
        return ByteArrayOutputStream().also { ImageIO.write(avatar, "png", it) }.toByteArray()
    }

    private fun decode(upload: ByteArray): BufferedImage =
        MemoryCacheImageInputStream(ByteArrayInputStream(upload)).use { input ->
            val reader =
                ImageIO
                    .getImageReaders(input)
                    .asSequence()
                    .firstOrNull { it.formatName.lowercase() in SUPPORTED_FORMATS }
                    ?: throw InvalidAvatarException("Avatar must be a PNG, JPEG or GIF image")
            try {
                reader.setInput(input, true, true)
                checkDimensions(reader)
                reader.read(0)
            } catch (_: IOException) {
                throw InvalidAvatarException("Avatar image could not be read")
            } finally {
                reader.dispose()
            }
        }

    /** Reads only the header, so an oversized image is rejected before its pixels are allocated. */
    private fun checkDimensions(reader: ImageReader) {
        val width = reader.getWidth(0)
        val height = reader.getHeight(0)
        if (width > MAX_DIMENSION || height > MAX_DIMENSION) {
            throw InvalidAvatarException("Avatar must be at most ${MAX_DIMENSION}x$MAX_DIMENSION pixels")
        }
    }

    private fun cropToSquare(image: BufferedImage): BufferedImage {
        val side = minOf(image.width, image.height)
        return image.getSubimage((image.width - side) / 2, (image.height - side) / 2, side, side)
    }

    /** Halves the image step by step: a single bilinear pass from a large photo would alias badly. */
    private fun resize(
        source: BufferedImage,
        target: Int,
    ): BufferedImage {
        var current = source
        while (current.width / 2 >= target) {
            current = scale(current, current.width / 2)
        }
        return if (current.width == target) current else scale(current, target)
    }

    private fun scale(
        source: BufferedImage,
        side: Int,
    ): BufferedImage {
        val scaled = BufferedImage(side, side, BufferedImage.TYPE_INT_ARGB)
        val graphics = scaled.createGraphics()
        try {
            graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR)
            graphics.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY)
            graphics.drawImage(source, 0, 0, side, side, null)
        } finally {
            graphics.dispose()
        }
        return scaled
    }

    companion object {
        const val SIZE = 256
        const val MAX_DIMENSION = 4096
        private val SUPPORTED_FORMATS = setOf("png", "jpeg", "gif")
    }
}
