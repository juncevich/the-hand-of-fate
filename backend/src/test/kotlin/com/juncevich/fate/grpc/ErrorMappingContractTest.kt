package com.juncevich.fate.grpc

import com.google.rpc.ErrorInfo
import com.juncevich.fate.platform.ErrorHandler
import com.juncevich.fate.shared.BadRequestException
import com.juncevich.fate.shared.ConflictException
import com.juncevich.fate.shared.FateException
import com.juncevich.fate.shared.ForbiddenException
import com.juncevich.fate.shared.NotFoundException
import com.juncevich.fate.shared.TooManyRequestsException
import io.grpc.Status
import io.grpc.protobuf.StatusProto
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.http.HttpStatus
import org.springframework.orm.ObjectOptimisticLockingFailureException
import java.sql.SQLException
import kotlin.reflect.KClass
import kotlin.reflect.full.primaryConstructor
import com.juncevich.fate.shared.ErrorCode as DomainErrorCode

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
            ConflictException::class to (HttpStatus.CONFLICT to Status.Code.FAILED_PRECONDITION),
            TooManyRequestsException::class to (HttpStatus.TOO_MANY_REQUESTS to Status.Code.RESOURCE_EXHAUSTED)
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
            val ex = checkNotNull(type.primaryConstructor).call("Something about ${type.simpleName}", DomainErrorCode.VOTE_NOT_FOUND)

            val rest = handler.handleDomain(ex)
            assertEquals(http, rest.statusCode, "REST status for ${type.simpleName}")
            assertEquals(ex.message, rest.body?.title, "REST title for ${type.simpleName}")
            assertEquals(DomainErrorCode.VOTE_NOT_FOUND, rest.body?.properties?.get("code"), "REST code for ${type.simpleName}")

            val status = GrpcErrors.toStatusException(ex).status
            assertEquals(grpc, status.code, "gRPC status for ${type.simpleName}")
            assertEquals(ex.message, status.description, "gRPC description for ${type.simpleName}")
            val info =
                StatusProto
                    .fromThrowable(GrpcErrors.toStatusException(ex))
                    ?.detailsList
                    ?.single()
                    ?.unpack(ErrorInfo::class.java)
            assertEquals("VOTE_NOT_FOUND", info?.reason, "gRPC reason for ${type.simpleName}")
        }
    }

    @Test
    fun `REST and gRPC report infrastructure failures that are client errors alike`() {
        val handler = ErrorHandler()
        val cases =
            mapOf(
                ObjectOptimisticLockingFailureException("Vote", 1L) to DomainErrorCode.CONCURRENT_MODIFICATION,
                DataIntegrityViolationException("SQL details", SQLException("duplicate", "23505")) to DomainErrorCode.ALREADY_EXISTS
            )
        cases.forEach { (ex, code) ->
            val rest = handler.handleInfrastructure(ex)
            assertEquals(HttpStatus.CONFLICT, rest.statusCode, "REST status for ${ex.javaClass.simpleName}")
            assertEquals(code, rest.body?.properties?.get("code"))

            val grpc = GrpcErrors.toStatusException(ex)
            assertEquals(Status.Code.FAILED_PRECONDITION, grpc.status.code, "gRPC status for ${ex.javaClass.simpleName}")
            assertEquals(rest.body?.title, grpc.status.description)
            assertEquals(
                code.name,
                StatusProto
                    .fromThrowable(grpc)
                    ?.detailsList
                    ?.single()
                    ?.unpack(ErrorInfo::class.java)
                    ?.reason
            )

            val failed = grpcMutation(failure = { message, protoCode -> message to protoCode }) { throw ex }
            assertEquals(rest.body?.title to code.toProto(), failed, "gRPC mutation result for ${ex.javaClass.simpleName}")
        }
    }

    @Test
    fun `any other integrity violation is internal on both transports`() {
        val ex = DataIntegrityViolationException("SQL details", SQLException("foreign key", "23503"))

        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, ErrorHandler().handleInfrastructure(ex).statusCode)
        assertEquals(Status.Code.INTERNAL, GrpcErrors.toStatusException(ex).status.code)
    }

    @Test
    fun `the proto ErrorCode mirrors the domain one`() {
        val proto = ErrorCode.entries.filter { it != ErrorCode.ERROR_CODE_UNSPECIFIED && it != ErrorCode.UNRECOGNIZED }
        assertEquals(DomainErrorCode.entries.map { "ERROR_CODE_${it.name}" }, proto.map { it.name })
        DomainErrorCode.entries.forEach { assertEquals("ERROR_CODE_${it.name}", it.toProto().name) }
    }
}
