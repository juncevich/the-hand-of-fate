package com.juncevich.fate.vote.config

import com.juncevich.fate.vote.domain.WinnerPicker
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

/** Beans of the vote domain, which itself carries no Spring annotations. */
@Configuration(proxyBeanMethods = false)
class VoteDomainConfig {
    @Bean
    fun winnerPicker() = WinnerPicker()
}
