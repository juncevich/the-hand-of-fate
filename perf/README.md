# perf — Load & Performance Tests

Gatling 3.13.5 + Kotlin. Standalone Gradle project targeting the backend REST API.

## Structure

```
perf/
├── build.gradle.kts
├── gradle.properties          # Gradle JVM flags
├── gradle/gradle-daemon-jvm.properties  # Gradle runs on Temurin 26 (auto-provisioned)
├── src/gatling/kotlin/simulations/
│   ├── Keycloak.kt            # signIn(): the demo user's access token from Keycloak
│   ├── SmokeSimulation.kt     # 1 user, all key endpoints, zero failures asserted
│   └── VoteSimulation.kt      # list + create + draw; p100 < 2s, >95% success
```

## Prerequisites

- Nothing to install for Java: Gradle provisions Temurin 26 for itself (`gradle/gradle-daemon-jvm.properties`) and for the Java 26 toolchain the simulations are compiled and run with.
- Running backend at `http://localhost:8080` (default) and Keycloak at `http://localhost:8180/auth` (`docker-compose.infra.yml`, default).
- The dev/e2e realm (`infra/keycloak/realm-dev/`): demo user `admin@admin.com` / `admin123` and the client `fate-test`, whose direct grants hand out tokens without a browser. Production realms have neither.

## Running

```bash
# All simulations sequentially
./gradlew gatlingRun

# Single simulation
./gradlew gatlingRun --simulation simulations.SmokeSimulation
./gradlew gatlingRun --simulation simulations.VoteSimulation

# Non-local target (Keycloak through the gateway)
./gradlew gatlingRun -DbaseUrl=https://your-server.com -DkeycloakUrl=https://your-server.com/auth

# minikube: Keycloak through the gateway, backend through a port-forward (actuator is not proxied)
kubectl --context hand-of-fate -n hand-of-fate port-forward svc/backend 18080:8080 &
./gradlew gatlingRun -DbaseUrl=http://localhost:18080 -DkeycloakUrl=http://localhost:30080/auth
```

Reports are written to `build/reports/gatling/<simulation-timestamp>/index.html`.

## Simulations

### SmokeSimulation
Single virtual user walks through:
1. `GET /actuator/health`
2. Sign-in through Keycloak (`fate-test`, direct grant)
3. `GET /api/v1/users/me` (creates the account on the backend on first use)
4. `GET /api/v1/votes`

Assertion: **zero failed requests**. Use as a quick post-deploy sanity check.

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
