package com.juncevich.fate

import net.devh.boot.grpc.server.autoconfigure.GrpcServerSecurityAutoConfiguration
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.context.properties.ConfigurationPropertiesScan
import org.springframework.boot.runApplication
import org.springframework.data.jpa.repository.config.EnableJpaAuditing
import org.springframework.scheduling.annotation.EnableAsync

@SpringBootApplication(exclude = [GrpcServerSecurityAutoConfiguration::class])
@ConfigurationPropertiesScan
@EnableJpaAuditing
@EnableAsync
class FateApplication

fun main(args: Array<String>) {
    runApplication<FateApplication>(*args)
}
