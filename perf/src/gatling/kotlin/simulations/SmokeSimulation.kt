package simulations

import io.gatling.javaapi.core.*
import io.gatling.javaapi.core.CoreDsl.*
import io.gatling.javaapi.http.HttpDsl.*

/** Минимальная проверка работоспособности — 1 пользователь, все ключевые эндпоинты. */
class SmokeSimulation : Simulation() {

    private val baseUrl = System.getProperty("baseUrl", "http://localhost:8080")

    private val httpProtocol = http
        .baseUrl(baseUrl)
        .acceptHeader("application/json")
        .contentTypeHeader("application/json")

    private val smoke = scenario("Smoke")
        .exec(
            http("Actuator Health")
                .get("/actuator/health")
                .check(status().`is`(200))
        )
        .pause(1)
        .exec(
            http("Login")
                .post("/api/v1/auth/login")
                .body(StringBody("""{"email":"admin@admin.com","password":"admin"}"""))
                .check(status().`is`(200))
                .check(jsonPath("$.accessToken").saveAs("accessToken"))
        )
        .pause(1)
        .exec(
            http("List Votes")
                .get("/api/v1/votes?page=0&size=10")
                .header("Authorization", "Bearer #{accessToken}")
                .check(status().`is`(200))
        )

    init {
        setUp(
            smoke.injectOpen(atOnceUsers(1))
        ).protocols(httpProtocol)
            .assertions(
                global().failedRequests().count().`is`(0)
            )
    }
}
