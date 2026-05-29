package simulations

import io.gatling.javaapi.core.*
import io.gatling.javaapi.core.CoreDsl.*
import io.gatling.javaapi.http.HttpDsl.*

class AuthSimulation : Simulation() {

    private val baseUrl = System.getProperty("baseUrl", "http://localhost:8080")

    private val httpProtocol = http
        .baseUrl(baseUrl)
        .acceptHeader("application/json")
        .contentTypeHeader("application/json")

    private val register = exec(
        http("Register")
            .post("/api/v1/auth/register")
            .body(StringBody("""{"email":"user_#{userId}@perf.test","password":"Password1!","displayName":"User #{userId}"}"""))
            .check(status().`is`(200))
            .check(jsonPath("$.accessToken").saveAs("accessToken"))
    )

    private val login = exec(
        http("Login")
            .post("/api/v1/auth/login")
            .body(StringBody("""{"email":"admin@admin.com","password":"admin"}"""))
            .check(status().`is`(200))
            .check(jsonPath("$.accessToken").saveAs("accessToken"))
    )

    private val refresh = exec(
        http("Silent Refresh")
            .post("/api/v1/auth/refresh")
            .body(StringBody("{}"))
            .check(status().`in`(200, 401))
    )

    private val authScenario = scenario("Auth Flow")
        .feed(LongFeeder("userId"))
        .exec(register)
        .pause(1)
        .exec(refresh)

    private val loginScenario = scenario("Login")
        .repeat(5).on(
            exec(login).pause(1)
        )

    init {
        setUp(
            authScenario.injectOpen(
                rampUsers(20).during(30)
            ),
            loginScenario.injectOpen(
                rampUsers(50).during(30)
            )
        ).protocols(httpProtocol)
    }

    private fun LongFeeder(name: String): Iterator<Map<String, Long>> =
        generateSequence(1L) { it + 1 }.map { mapOf(name to it) }.iterator()
}
