package com.juncevich.fate.grpc

import org.springframework.boot.grpc.server.autoconfigure.GrpcServerExecutorProvider
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import java.util.concurrent.Executors

@Configuration(proxyBeanMethods = false)
class GrpcServerConfig {
    /**
     * `spring.threads.virtual.enabled` covers Tomcat and `@Async` but not the gRPC server, which
     * otherwise falls back to grpc-java's cached platform-thread pool for call callbacks.
     * The executor is deliberately not exposed as a bean: an `Executor` bean would make Spring Boot
     * back off from its virtual-thread `applicationTaskExecutor` that `@Async` relies on.
     */
    @Bean
    fun grpcServerExecutorProvider(): GrpcServerExecutorProvider {
        val executor = Executors.newVirtualThreadPerTaskExecutor()
        return GrpcServerExecutorProvider { executor }
    }
}
