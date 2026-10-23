package com.juncevich.fate.account

import com.juncevich.fate.account.service.AvatarImageProcessor
import com.juncevich.fate.account.service.InvalidAvatarException
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import java.awt.Color
import java.awt.image.BufferedImage
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import javax.imageio.ImageIO

class AvatarImageProcessorTest {
    private val processor = AvatarImageProcessor()

    private fun image(
        width: Int,
        height: Int,
        format: String = "png",
        type: Int = BufferedImage.TYPE_INT_RGB,
    ): ByteArray {
        val image = BufferedImage(width, height, type)
        image.createGraphics().apply {
            color = Color.RED
            fillRect(0, 0, width, height)
            dispose()
        }
        return ByteArrayOutputStream().also { ImageIO.write(image, format, it) }.toByteArray()
    }

    private fun decode(bytes: ByteArray): BufferedImage = ImageIO.read(ByteArrayInputStream(bytes))

    @ParameterizedTest
    @CsvSource("png, 1200, 800", "jpeg, 640, 900", "gif, 300, 300", "png, 256, 256", "png, 40, 60")
    fun `re-encodes supported images as a square png of the avatar size`(
        format: String,
        width: Int,
        height: Int,
    ) {
        val result = processor.toAvatarPng(image(width, height, format))

        val decoded = decode(result)
        assertEquals(AvatarImageProcessor.SIZE, decoded.width)
        assertEquals(AvatarImageProcessor.SIZE, decoded.height)
        // PNG signature: the output is always a PNG, whatever was uploaded
        assertEquals(0x89.toByte(), result[0])
        assertEquals('P'.code.toByte(), result[1])
    }

    @Test
    fun `keeps the center of a non-square image`() {
        val wide = BufferedImage(600, 200, BufferedImage.TYPE_INT_RGB)
        wide.createGraphics().apply {
            color = Color.BLUE
            fillRect(0, 0, 600, 200)
            color = Color.GREEN
            fillRect(200, 0, 200, 200)
            dispose()
        }
        val bytes = ByteArrayOutputStream().also { ImageIO.write(wide, "png", it) }.toByteArray()

        val decoded = decode(processor.toAvatarPng(bytes))

        assertEquals(Color.GREEN.rgb, decoded.getRGB(0, 0) or 0xFF000000.toInt())
        assertEquals(Color.GREEN.rgb, decoded.getRGB(255, 255) or 0xFF000000.toInt())
    }

    @Test
    fun `rejects an empty upload`() {
        val error = assertThrows<InvalidAvatarException> { processor.toAvatarPng(ByteArray(0)) }
        assertEquals("Avatar file is empty", error.message)
    }

    @Test
    fun `rejects content that is not an image`() {
        val error = assertThrows<InvalidAvatarException> { processor.toAvatarPng("<svg/>".toByteArray()) }
        assertEquals("Avatar must be a PNG, JPEG or GIF image", error.message)
    }

    @Test
    fun `rejects an unsupported image format`() {
        assertThrows<InvalidAvatarException> { processor.toAvatarPng(image(10, 10, "bmp")) }
    }

    @Test
    fun `rejects a truncated image`() {
        val truncated = image(300, 300, "jpeg").copyOf(200)
        val error = assertThrows<InvalidAvatarException> { processor.toAvatarPng(truncated) }
        assertEquals("Avatar image could not be read", error.message)
    }

    @Test
    fun `rejects images larger than the maximum dimension before decoding them`() {
        val tooWide = image(AvatarImageProcessor.MAX_DIMENSION + 1, 1, type = BufferedImage.TYPE_BYTE_BINARY)
        val error = assertThrows<InvalidAvatarException> { processor.toAvatarPng(tooWide) }
        assertEquals("Avatar must be at most 4096x4096 pixels", error.message)

        val tooTall = image(1, AvatarImageProcessor.MAX_DIMENSION + 1, type = BufferedImage.TYPE_BYTE_BINARY)
        assertThrows<InvalidAvatarException> { processor.toAvatarPng(tooTall) }
    }
}
