package com.juncevich.fate.grpc

import org.springframework.boot.grpc.server.autoconfigure.GrpcServerExecutorProvider
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

@Configuration(proxyBeanMethods = false)
class GrpcServerConfig {
    /**
     * `spring.threads.virtual.enabled` covers Tomcat and `@Async` but not the gRPC server, which
     * otherwise falls back to grpc-java's cached platform-thread pool for call callbacks. RPC bodies
     * make blocking JPA calls, so each call runs on its own virtual thread.
     * The executor is deliberately not exposed as a bean: an `Executor` bean would make Spring Boot
     * back off from its virtual-thread `applicationTaskExecutor` that `@Async` relies on.
     */
    @Bean
    fun grpcServerExecutorProvider(): GrpcServerExecutorProvider = VirtualThreadExecutorProvider()
}

/** Shut down with the context (Spring calls [close] on an `AutoCloseable` bean), after the gRPC server stops. */
private class VirtualThreadExecutorProvider :
    GrpcServerExecutorProvider,
    AutoCloseable {
    private val executor: ExecutorService = Executors.newVirtualThreadPerTaskExecutor()

    override fun getExecutor(): ExecutorService = executor

    override fun close() = executor.shutdown()
}
