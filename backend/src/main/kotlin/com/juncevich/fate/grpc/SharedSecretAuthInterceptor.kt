package com.juncevich.fate.grpc

import io.grpc.Metadata
import io.grpc.ServerCall
import io.grpc.ServerCallHandler
import io.grpc.ServerInterceptor
import io.grpc.Status
import net.devh.boot.grpc.server.interceptor.GrpcGlobalServerInterceptor
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import java.security.MessageDigest

@Component
@GrpcGlobalServerInterceptor
class SharedSecretAuthInterceptor(
    @Value("\${grpc.shared-secret}") private val sharedSecret: String,
) : ServerInterceptor {
    private val log = LoggerFactory.getLogger(javaClass)

    override fun <ReqT : Any, RespT : Any> interceptCall(
        call: ServerCall<ReqT, RespT>,
        headers: Metadata,
        next: ServerCallHandler<ReqT, RespT>,
    ): ServerCall.Listener<ReqT> {
        val provided = headers.get(SHARED_SECRET_KEY)
        if (provided == null || !constantTimeEquals(provided, sharedSecret)) {
            log.warn(
                "Rejected gRPC call with missing or invalid shared secret: {}",
                call.methodDescriptor.fullMethodName
            )
            call.close(Status.UNAUTHENTICATED.withDescription("Missing or invalid shared secret"), Metadata())
            return object : ServerCall.Listener<ReqT>() {}
        }
        return next.startCall(call, headers)
    }

    private fun constantTimeEquals(
        a: String,
        b: String,
    ): Boolean = MessageDigest.isEqual(a.toByteArray(), b.toByteArray())

    companion object {
        val SHARED_SECRET_KEY: Metadata.Key<String> =
            Metadata.Key.of("x-grpc-shared-secret", Metadata.ASCII_STRING_MARSHALLER)
    }
}
