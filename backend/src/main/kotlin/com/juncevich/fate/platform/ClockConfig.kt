package com.juncevich.fate.platform

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.data.auditing.DateTimeProvider
import java.time.Clock
import java.util.Optional

/**
 * The one source of "now" for services and workers; the domain never reads the clock itself but
 * takes the time as a parameter. Tests pass a fixed [Clock] instead.
 */
@Configuration(proxyBeanMethods = false)
class ClockConfig {
    @Bean
    fun clock(): Clock = Clock.systemUTC()

    /** Spring Data auditing (`@LastModifiedDate`) reads the same clock. */
    @Bean
    fun auditingDateTimeProvider(clock: Clock): DateTimeProvider = DateTimeProvider { Optional.of(clock.instant()) }
}
