package simulations

import io.gatling.javaapi.core.*
import io.gatling.javaapi.core.CoreDsl.*
import io.gatling.javaapi.http.HttpDsl.*

class VoteSimulation : Simulation() {

    private val baseUrl = System.getProperty("baseUrl", "http://localhost:8080")

    private val httpProtocol = http
        .baseUrl(baseUrl)
        .acceptHeader("application/json")
        .contentTypeHeader("application/json")

    private val login = exec(
        http("Login")
            .post("/api/v1/auth/login")
            .body(StringBody("""{"email":"admin@admin.com","password":"admin"}"""))
            .check(status().`is`(200))
            .check(jsonPath("$.accessToken").saveAs("accessToken"))
    )

    private val listVotes = exec(
        http("List Votes")
            .get("/api/v1/votes?page=0&size=20")
            .header("Authorization", "Bearer #{accessToken}")
            .check(status().`is`(200))
    )

    private val createVote = exec(
        http("Create Vote")
            .post("/api/v1/votes")
            .header("Authorization", "Bearer #{accessToken}")
            .body(StringBody("""{"title":"Perf Vote #{voteIdx}","mode":"SIMPLE"}"""))
            .check(status().`is`(200))
            .check(jsonPath("$.id").saveAs("voteId"))
    )

    private val getVote = exec(
        http("Get Vote")
            .get("/api/v1/votes/#{voteId}")
            .header("Authorization", "Bearer #{accessToken}")
            .check(status().`in`(200, 404))
    )

    private val performDraw = exec(
        http("Perform Draw")
            .post("/api/v1/votes/#{voteId}/draw")
            .header("Authorization", "Bearer #{accessToken}")
            .check(status().`in`(200, 400, 409))
    )

    private val browseScenario = scenario("Browse Votes")
        .exec(login)
        .pause(1)
        .repeat(10).on(
            exec(listVotes).pause(2)
        )

    private val crudScenario = scenario("Create and Draw")
        .feed(LongFeeder("voteIdx"))
        .exec(login)
        .pause(1)
        .exec(createVote)
        .pause(1)
        .exec(getVote)
        .pause(1)
        .exec(performDraw)

    init {
        setUp(
            browseScenario.injectOpen(
                atOnceUsers(10),
                rampUsers(40).during(60)
            ),
            crudScenario.injectOpen(
                nothingFor(5),
                rampUsers(20).during(60)
            )
        ).protocols(httpProtocol)
            .assertions(
                global().responseTime().max().lt(2000),
                global().successfulRequests().percent().gt(95.0)
            )
    }

    private fun LongFeeder(name: String): Iterator<Map<String, Long>> =
        generateSequence(1L) { it + 1 }.map { mapOf(name to it) }.iterator()
}
