package com.juncevich.fate.grpc

import com.google.rpc.ErrorInfo
import com.juncevich.fate.shared.BadRequestException
import com.juncevich.fate.shared.ConflictException
import com.juncevich.fate.shared.FateException
import com.juncevich.fate.shared.ForbiddenException
import com.juncevich.fate.shared.NotFoundException
import io.grpc.Status
import io.grpc.StatusRuntimeException
import io.grpc.protobuf.StatusProto
import com.google.protobuf.Any as ProtoAny

/**
 * gRPC counterpart of the REST `ErrorHandler`: the single place that decides which failures are
 * client errors (status + message exposed) and which are bugs (`INTERNAL`, message hidden).
 */
internal object GrpcErrors {
    const val ERROR_DOMAIN = "fate.v1"
    const val REASON_TELEGRAM_NOT_LINKED = "TELEGRAM_NOT_LINKED"

    /** Status for a client error, or `null` when [ex] is unexpected and must be reported as `INTERNAL`. */
    fun clientStatus(ex: Throwable): Status? = (ex as? FateException)?.grpcStatus()

    /** Converts any failure into the exception a read RPC should throw. */
    fun toStatusException(ex: Throwable): StatusRuntimeException =
        when (ex) {
            is StatusRuntimeException -> ex
            else -> clientStatus(ex)?.withDescription(ex.message)?.asRuntimeException() ?: internal()
        }

    /**
     * The caller's Telegram account is not linked to an app user. Keeps `NOT_FOUND` for older bots
     * and adds an `ErrorInfo` so clients can tell it apart from a missing vote (see fate.proto).
     */
    fun telegramNotLinked(): StatusRuntimeException =
        StatusProto.toStatusRuntimeException(
            com.google.rpc.Status
                .newBuilder()
                .setCode(Status.Code.NOT_FOUND.value())
                .setMessage("Telegram account not linked")
                .addDetails(
                    ProtoAny.pack(
                        ErrorInfo
                            .newBuilder()
                            .setReason(REASON_TELEGRAM_NOT_LINKED)
                            .setDomain(ERROR_DOMAIN)
                            .build()
                    )
                ).build()
        )

    fun internal(): StatusRuntimeException = Status.INTERNAL.withDescription("Unexpected error").asRuntimeException()

    private fun FateException.grpcStatus(): Status =
        when (this) {
            is BadRequestException -> Status.INVALID_ARGUMENT
            is NotFoundException -> Status.NOT_FOUND
            is ForbiddenException -> Status.PERMISSION_DENIED
            is ConflictException -> Status.FAILED_PRECONDITION
        }
}

/** Runs a read RPC body, translating failures via [GrpcErrors.toStatusException]. */
internal inline fun <T> grpcRead(block: () -> T): T =
    runCatching(block).getOrElse { throw GrpcErrors.toStatusException(it) }

/**
 * Runs a mutating RPC body whose response carries `success`/`message`: client errors become
 * an unsuccessful response built by [failure], anything unexpected becomes `INTERNAL`.
 */
internal inline fun <T> grpcMutation(
    failure: (message: String) -> T,
    block: () -> T,
): T =
    runCatching(block).getOrElse { ex ->
        when (ex) {
            is StatusRuntimeException -> throw ex
            is FateException -> failure(ex.message.orEmpty())
            else -> throw GrpcErrors.internal()
        }
    }
