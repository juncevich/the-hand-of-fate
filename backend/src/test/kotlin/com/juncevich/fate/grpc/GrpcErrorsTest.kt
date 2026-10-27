package com.juncevich.fate.grpc

import ch.qos.logback.classic.Level
import ch.qos.logback.classic.Logger
import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.core.read.ListAppender
import com.juncevich.fate.shared.NotFoundException
import io.grpc.Status
import io.grpc.StatusRuntimeException
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.slf4j.LoggerFactory
import com.juncevich.fate.shared.ErrorCode as DomainErrorCode

/** Unexpected failures must leave a stack trace in the log, like REST's 500s; client errors must not. */
class GrpcErrorsTest {
    private val logger = LoggerFactory.getLogger(GrpcErrors::class.java) as Logger
    private val appender = ListAppender<ILoggingEvent>()

    @BeforeEach
    fun attach() {
        appender.start()
        logger.addAppender(appender)
    }

    @AfterEach
    fun detach() {
        logger.detachAppender(appender)
    }

    @Test
    fun `a bug in a read is logged with its stack trace and reported as a generic INTERNAL`() {
        val bug = IllegalStateException("secret detail")

        val status = GrpcErrors.toStatusException(bug).status

        assertEquals(Status.Code.INTERNAL, status.code)
        assertEquals("Unexpected error", status.description)
        val event = appender.list.single()
        assertEquals(Level.ERROR, event.level)
        assertSame(bug, (event.throwableProxy as ch.qos.logback.classic.spi.ThrowableProxy).throwable)
    }

    @Test
    fun `a bug in a mutation is logged too`() {
        assertThrows<StatusRuntimeException> {
            grpcMutation(failure = { _, _ -> }) { error("broken invariant") }
        }

        assertEquals(Level.ERROR, appender.list.single().level)
    }

    @Test
    fun `client errors and existing statuses are not logged as errors`() {
        GrpcErrors.toStatusException(NotFoundException("Vote not found", DomainErrorCode.VOTE_NOT_FOUND))
        val status = GrpcErrors.telegramNotLinked()
        assertSame(status, GrpcErrors.toStatusException(status))

        assertTrue(appender.list.isEmpty())
    }
}
