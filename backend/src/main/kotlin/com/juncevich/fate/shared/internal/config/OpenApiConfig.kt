package com.juncevich.fate.shared.internal.config

import com.juncevich.fate.shared.OpenApiDocs
import io.swagger.v3.core.converter.AnnotatedType
import io.swagger.v3.core.converter.ModelConverter
import io.swagger.v3.core.converter.ModelConverterContext
import io.swagger.v3.core.util.Json
import io.swagger.v3.oas.models.Components
import io.swagger.v3.oas.models.OpenAPI
import io.swagger.v3.oas.models.info.Info
import io.swagger.v3.oas.models.info.License
import io.swagger.v3.oas.models.media.Content
import io.swagger.v3.oas.models.media.IntegerSchema
import io.swagger.v3.oas.models.media.MediaType
import io.swagger.v3.oas.models.media.ObjectSchema
import io.swagger.v3.oas.models.media.Schema
import io.swagger.v3.oas.models.media.StringSchema
import io.swagger.v3.oas.models.responses.ApiResponse
import io.swagger.v3.oas.models.security.SecurityRequirement
import io.swagger.v3.oas.models.security.SecurityScheme
import io.swagger.v3.oas.models.servers.Server
import org.springdoc.core.customizers.OpenApiCustomizer
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import kotlin.reflect.full.primaryConstructor

/**
 * Shapes the springdoc-generated spec that is committed as `api/openapi.yaml` (see
 * `OpenApiSpecIntegrationTest`): metadata, bearer auth as the default security, and a shared
 * `ProblemDetail` error body. Only served when `springdoc.api-docs.enabled` is on.
 */
@Configuration
class OpenApiConfig {
    @Bean
    fun openApi(): OpenAPI =
        OpenAPI()
            .info(
                Info()
                    .title("The Hand of Fate API")
                    .version("v1")
                    .license(License().name("MIT").url("https://opensource.org/licenses/MIT"))
                    .description(
                        "REST API of The Hand of Fate. Errors are returned as RFC 9457 problem details " +
                            "whose `title` is the message to show to the user."
                    )
            )
            // Relative, so the spec does not depend on the host it was generated on.
            .servers(listOf(Server().url("/")))
            .components(
                Components()
                    .addSecuritySchemes(
                        OpenApiDocs.BEARER,
                        SecurityScheme()
                            .type(SecurityScheme.Type.HTTP)
                            .scheme("bearer")
                            .bearerFormat("JWT")
                            .description("Access token returned by login/register/refresh")
                    ).addSchemas(OpenApiDocs.PROBLEM_SCHEMA, problemSchema())
            ).addSecurityItem(SecurityRequirement().addList(OpenApiDocs.BEARER))

    /**
     * Adds the error responses every operation shares (`401` where an access token is required,
     * a `default` problem detail for the remaining 4xx/5xx statuses), drops the `content`
     * springdoc infers from the return type for bodiless responses and sorts tags so the
     * committed spec stays stable.
     */
    @Bean
    fun errorResponsesCustomizer(): OpenApiCustomizer =
        OpenApiCustomizer { openApi ->
            val requiresToken = { security: List<SecurityRequirement>? ->
                security == null || security.any { it.containsKey(OpenApiDocs.BEARER) }
            }
            openApi.paths.orEmpty().values.flatMap { it.readOperations() }.forEach { operation ->
                operation.responses
                    .filter { (status, response) -> status in BODILESS_STATUSES || response.content?.isEmpty() == true }
                    .forEach { (_, response) -> response.content = null }
                if (requiresToken(operation.security) && "401" !in operation.responses) {
                    operation.responses.addApiResponse(
                        "401",
                        ApiResponse().description("Missing or invalid access token")
                    )
                }
                operation.responses.addApiResponse("default", problemResponse("Error described by a problem detail"))
            }
            openApi.tags = openApi.tags?.sortedBy { it.name }
            // A nullable reference is emitted as `allOf: [$ref], nullable: true`; OpenAPI 3.0 only
            // honours `nullable` next to an explicit `type`.
            openApi.components.schemas.values
                .flatMap { it.properties.orEmpty().values }
                .filter { it.nullable == true && it.type == null && !it.allOf.isNullOrEmpty() }
                .forEach { it.type = "object" }
        }

    @Bean
    fun kotlinRequiredPropertiesConverter(): ModelConverter = KotlinRequiredPropertiesConverter()

    private fun problemSchema(): Schema<*> =
        ObjectSchema()
            .description("RFC 9457 problem detail")
            .addProperty("type", StringSchema().format("uri"))
            .addProperty("title", StringSchema().description("Human-readable message to show to the user"))
            .addProperty("status", IntegerSchema())
            .addProperty("detail", StringSchema())
            .addProperty("instance", StringSchema().format("uri"))
            .addProperty("timestamp", StringSchema().format("date-time"))
            .addProperty(
                "code",
                StringSchema().description(
                    "Stable machine-readable reason, present on some failures (e.g. EMAIL_NOT_VERIFIED)"
                )
            ).addProperty(
                "errors",
                ObjectSchema()
                    .description("Field name to message, present on validation failures")
                    .additionalProperties(StringSchema())
            ).required(listOf("title", "status"))

    private companion object {
        val BODILESS_STATUSES = setOf("204", "304")
    }

    private fun problemResponse(description: String): ApiResponse =
        ApiResponse()
            .description(description)
            .content(
                Content().addMediaType(
                    OpenApiDocs.PROBLEM_MEDIA_TYPE,
                    MediaType().schema(Schema<Any>().`$ref`(OpenApiDocs.PROBLEM_REF))
                )
            )
}

/**
 * Marks a Kotlin class's properties as required when its primary constructor parameter has no
 * default value. Jackson writes `null`s, so such a property is always present in a response —
 * nullable ones as `type: [..., "null"]`. Bean validation alone only covers `@NotBlank`/`@NotNull`
 * fields, which would leave every response field optional for generated clients.
 */
internal class KotlinRequiredPropertiesConverter : ModelConverter {
    override fun resolve(
        type: AnnotatedType,
        context: ModelConverterContext,
        chain: Iterator<ModelConverter>,
    ): Schema<*>? {
        if (!chain.hasNext()) return null
        val schema = chain.next().resolve(type, context, chain) ?: return null
        // Top-level types resolve to a `$ref`; the full schema is the one defined in the context.
        val model = schema.`$ref`?.let { context.definedModels[it.substringAfterLast('/')] } ?: schema
        val properties = model.properties.orEmpty()
        val rawClass = Json.mapper().constructType(type.type).rawClass
        val constructor = rawClass.takeIf { it.isAnnotationPresent(Metadata::class.java) }?.kotlin?.primaryConstructor
        val required =
            constructor
                ?.parameters
                .orEmpty()
                .filterNot { it.isOptional }
                .mapNotNull { it.name }
                .filter { it in properties }
        if (required.isNotEmpty()) {
            model.required = (model.required.orEmpty() + required).distinct().sorted()
        }
        return schema
    }
}
