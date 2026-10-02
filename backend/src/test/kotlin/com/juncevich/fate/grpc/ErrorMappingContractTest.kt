package com.juncevich.fate.grpc

import com.juncevich.fate.shared.BadRequestException
import com.juncevich.fate.shared.ConflictException
import com.juncevich.fate.shared.FateException
import com.juncevich.fate.shared.ForbiddenException
import com.juncevich.fate.shared.NotFoundException
import com.juncevich.fate.shared.internal.config.ErrorHandler
import io.grpc.Status
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.springframework.http.HttpStatus
import kotlin.reflect.KClass
import kotlin.reflect.full.primaryConstructor

/**
 * REST and gRPC must agree on what each error category means. Adding a [FateException]
 * category without extending [expected] fails here instead of silently mapping it differently.
 */
class ErrorMappingContractTest {
    private val expected: Map<KClass<out FateException>, Pair<HttpStatus, Status.Code>> =
        mapOf(
            BadRequestException::class to (HttpStatus.BAD_REQUEST to Status.Code.INVALID_ARGUMENT),
            NotFoundException::class to (HttpStatus.NOT_FOUND to Status.Code.NOT_FOUND),
            ForbiddenException::class to (HttpStatus.FORBIDDEN to Status.Code.PERMISSION_DENIED),
            ConflictException::class to (HttpStatus.CONFLICT to Status.Code.FAILED_PRECONDITION)
        )

    @Test
    fun `every error category has a declared mapping`() {
        assertEquals(FateException::class.sealedSubclasses.toSet(), expected.keys)
    }

    @Test
    fun `REST and gRPC map every category consistently and expose its message`() {
        val handler = ErrorHandler()
        expected.forEach { (type, statuses) ->
            val (http, grpc) = statuses
            val ex = checkNotNull(type.primaryConstructor).call("Something about ${type.simpleName}")

            val rest = handler.handleDomain(ex)
            assertEquals(http, rest.statusCode, "REST status for ${type.simpleName}")
            assertEquals(ex.message, rest.body?.title, "REST title for ${type.simpleName}")

            val status = GrpcErrors.toStatusException(ex).status
            assertEquals(grpc, status.code, "gRPC status for ${type.simpleName}")
            assertEquals(ex.message, status.description, "gRPC description for ${type.simpleName}")
        }
    }
}
