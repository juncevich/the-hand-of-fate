package com.juncevich.fate.vote.web

import com.juncevich.fate.vote.DrawTarget
import org.springdoc.core.customizers.OpenApiCustomizer
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

/** Docs of the vote API's shared schemas, kept out of the module's own types. */
@Configuration(proxyBeanMethods = false)
class VoteOpenApiDocs {
    @Bean
    fun voteSchemaDocs(): OpenApiCustomizer =
        OpenApiCustomizer { openApi ->
            openApi.components
                ?.schemas
                ?.get(DrawTarget::class.simpleName)
                ?.description =
                "What a draw picks the winner from; fixed when the vote is created. " +
                "With OPTIONS the participants are observers and are never drawn."
        }
}
