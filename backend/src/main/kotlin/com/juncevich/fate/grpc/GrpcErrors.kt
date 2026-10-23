package com.juncevich.fate.grpc

import com.google.rpc.ErrorInfo
import com.juncevich.fate.shared.BadRequestException
import com.juncevich.fate.shared.ConflictException
import com.juncevich.fate.shared.FateException
import com.juncevich.fate.shared.ForbiddenException
import com.juncevich.fate.shared.NotFoundException
import com.juncevich.fate.shared.TooManyRequestsException
import io.grpc.Status
import io.grpc.StatusRuntimeException
import io.grpc.protobuf.StatusProto
import io.grpc.stub.StreamObserver
import com.google.protobuf.Any as ProtoAny
import com.juncevich.fate.shared.ErrorCode as DomainErrorCode

/**
 * gRPC counterpart of the REST `ErrorHandler`: the single place that decides which failures are
 * client errors (status + message exposed) and which are bugs (`INTERNAL`, message hidden).
 */
internal object GrpcErrors {
    const val ERROR_DOMAIN = "fate.v1"

    /** Status for a client error, or `null` when [ex] is unexpected and must be reported as `INTERNAL`. */
    fun clientStatus(ex: Throwable): Status? = (ex as? FateException)?.grpcStatus()

    /** Converts any failure into the exception a read RPC should throw. */
    fun toStatusException(ex: Throwable): StatusRuntimeException =
        when (ex) {
            is StatusRuntimeException -> ex
            is FateException -> status(ex.grpcStatus().code, ex.code, ex.message.orEmpty())
            else -> internal()
        }

    /** An expected failure: [code] with an `ErrorInfo` whose reason is [reason] (see fate.proto). */
    fun status(
        code: Status.Code,
        reason: DomainErrorCode,
        message: String,
    ): StatusRuntimeException =
        StatusProto.toStatusRuntimeException(
            com.google.rpc.Status
                .newBuilder()
                .setCode(code.value())
                .setMessage(message)
                .addDetails(
                    ProtoAny.pack(
                        ErrorInfo
                            .newBuilder()
                            .setReason(reason.name)
                            .setDomain(ERROR_DOMAIN)
                            .build()
                    )
                ).build()
        )

    fun invalidArgument(
        reason: DomainErrorCode,
        message: String,
    ) = status(Status.Code.INVALID_ARGUMENT, reason, message)

    /** The caller's Telegram account is not linked to an app user (`NOT_FOUND`, like a missing vote). */
    fun telegramNotLinked(): StatusRuntimeException =
        status(Status.Code.NOT_FOUND, DomainErrorCode.TELEGRAM_NOT_LINKED, "Telegram account not linked")

    fun internal(): StatusRuntimeException = Status.INTERNAL.withDescription("Unexpected error").asRuntimeException()

    private fun FateException.grpcStatus(): Status =
        when (this) {
            is BadRequestException -> Status.INVALID_ARGUMENT
            is NotFoundException -> Status.NOT_FOUND
            is ForbiddenException -> Status.PERMISSION_DENIED
            is ConflictException -> Status.FAILED_PRECONDITION
            is TooManyRequestsException -> Status.RESOURCE_EXHAUSTED
        }
}

/**
 * Completes a unary call with what [block] returns, or fails it with the status
 * [GrpcErrors.toStatusException] picks — the recovery boundary of every RPC body. `Error`s are
 * not caught: grpc-java closes the call itself.
 */
@Suppress("TooGenericExceptionCaught")
internal inline fun <T> unary(
    observer: StreamObserver<T>,
    block: () -> T,
) {
    val response =
        try {
            block()
        } catch (ex: Exception) {
            observer.onError(GrpcErrors.toStatusException(ex))
            return
        }
    observer.onNext(response)
    observer.onCompleted()
}

/** Runs a read RPC body, translating failures via [GrpcErrors.toStatusException]. */
@Suppress("TooGenericExceptionCaught")
internal inline fun <T> grpcRead(block: () -> T): T =
    try {
        block()
    } catch (ex: Exception) {
        throw GrpcErrors.toStatusException(ex)
    }

/**
 * Runs a mutating RPC body whose response carries `success`/`message`: client errors become
 * an unsuccessful response built by [failure] (English message + code), anything unexpected becomes `INTERNAL`.
 */
@Suppress("TooGenericExceptionCaught")
internal inline fun <T> grpcMutation(
    failure: (message: String, code: ErrorCode) -> T,
    block: () -> T,
): T =
    try {
        block()
    } catch (ex: StatusRuntimeException) {
        throw ex
    } catch (ex: FateException) {
        failure(ex.message.orEmpty(), ex.code.toProto())
    } catch (_: Exception) {
        throw GrpcErrors.internal()
    }

/** The proto mirror of a domain code (`ERROR_CODE_` + name; `ErrorMappingContractTest` keeps them in sync). */
internal fun DomainErrorCode.toProto(): ErrorCode = ErrorCode.valueOf("ERROR_CODE_$name")
