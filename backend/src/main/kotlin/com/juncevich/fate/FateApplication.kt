package com.juncevich.fate

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.context.properties.ConfigurationPropertiesScan
import org.springframework.boot.grpc.server.autoconfigure.security.GrpcServerOAuth2ResourceServerAutoConfiguration
import org.springframework.boot.grpc.server.autoconfigure.security.GrpcServerSecurityAutoConfiguration
import org.springframework.boot.runApplication
import org.springframework.data.jpa.repository.config.EnableJpaAuditing
import org.springframework.modulith.Modulith
import org.springframework.resilience.annotation.EnableResilientMethods
import org.springframework.scheduling.annotation.EnableScheduling

// gRPC callers (the Telegram bot) authenticate with the shared secret only
// (SharedSecretAuthInterceptor); keep Spring Security's JWT resource server off the gRPC server.
@SpringBootApplication(
    exclude = [GrpcServerSecurityAutoConfiguration::class, GrpcServerOAuth2ResourceServerAutoConfiguration::class]
)
@Modulith(systemName = "The Hand of Fate")
@ConfigurationPropertiesScan
@EnableJpaAuditing
@EnableScheduling
@EnableResilientMethods
class FateApplication

fun main(args: Array<String>) {
    runApplication<FateApplication>(*args)
}
