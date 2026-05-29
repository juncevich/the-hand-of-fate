# perf — Load & Performance Tests

Gatling 3.13.5 + Kotlin. Standalone Gradle project targeting the backend REST API.

## Structure

```
perf/
├── build.gradle.kts
├── gradle.properties          # Java 17 path + JVM flags
├── src/gatling/kotlin/simulations/
│   ├── SmokeSimulation.kt     # 1 user, all key endpoints, zero failures asserted
│   ├── AuthSimulation.kt      # register + silent refresh ramp; login burst
│   └── VoteSimulation.kt      # list + create + draw; p100 < 2s, >95% success
```

## Prerequisites

- Java 17 (Temurin). Path is set in `gradle.properties`; update it if your local path differs.
- Running backend at `http://localhost:8080` (default).
- Seeded demo user `admin@admin.com` / `admin` (migration V7).

## Running

```bash
# All simulations sequentially
./gradlew gatlingRun

# Single simulation
./gradlew gatlingRun --simulation simulations.SmokeSimulation
./gradlew gatlingRun --simulation simulations.AuthSimulation
./gradlew gatlingRun --simulation simulations.VoteSimulation

# Non-local target
./gradlew gatlingRun -DbaseUrl=https://your-server.com
```

Reports are written to `build/reports/gatling/<simulation-timestamp>/index.html`.

## Simulations

### SmokeSimulation
Single virtual user walks through:
1. `GET /actuator/health`
2. `POST /api/v1/auth/login`
3. `GET /api/v1/votes`

Assertion: **zero failed requests**. Use as a quick post-deploy sanity check.

### AuthSimulation
Two concurrent scenarios:

| Scenario | Load |
|---|---|
| Register → silent refresh | ramp 20 users / 30 s |
| Login (×5 per user) | ramp 50 users / 30 s |

No assertions — used to observe latency under auth pressure.

### VoteSimulation
Two concurrent scenarios:

| Scenario | Load |
|---|---|
| Browse votes (×10 list calls) | 10 at once + ramp 40 / 60 s |
| Create vote → get → draw | ramp 20 users / 60 s, starts after 5 s |

Assertions:
- Max response time **< 2 000 ms**
- Successful requests **> 95 %**

## Adding a Simulation

1. Create a file in `src/gatling/kotlin/simulations/`.
2. Extend `Simulation` and call `setUp(...).protocols(httpProtocol)` in the `init` block.
3. Run with `./gradlew gatlingRun --simulation simulations.YourSimulation`.

## Gatling DSL Quick Reference

```kotlin
// Inject strategies
atOnceUsers(10)
rampUsers(50).during(30)          // seconds
nothingFor(5)                     // seconds

// Assertions
global().responseTime().max().lt(2000)
global().successfulRequests().percent().gt(95.0)
global().failedRequests().count().is(0)

// Saving response values
.check(jsonPath("$.id").saveAs("voteId"))

// Using saved values
.header("Authorization", "Bearer #{accessToken}")
.body(StringBody("""{"title":"#{voteIdx}"}"""))
```
