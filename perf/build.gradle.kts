plugins {
    kotlin("jvm") version "2.4.20"
    id("io.gatling.gradle") version "3.15.1.3"
    id("com.diffplug.spotless") version "8.10.2"
    id("dev.detekt") version "2.0.0-alpha.6"
}

repositories {
    mavenCentral()
}

// Simulations compile with the Java 26 toolchain; the Gradle plugin runs them in the Gradle
// daemon's JVM, which is also Java 26 (gradle/gradle-daemon-jvm.properties).
kotlin {
    jvmToolchain(26)
}

dependencies {
    gatling("io.gatling.highcharts:gatling-charts-highcharts:3.15.1")
}

gatling {
    jvmArgs = listOf(
        "-server",
        "-Xmx1g",
        // Gatling's stats writer uses reflection into java.lang internals; required
        // on JDK 9+ where module encapsulation blocks this by default.
        "--add-opens=java.base/java.lang=ALL-UNNAMED",
        "-XX:+UseG1GC",
        "-XX:MaxGCPauseMillis=30",
        "-XX:G1HeapRegionSize=16m",
        "-XX:InitiatingHeapOccupancyPercent=75",
        "-XX:+ParallelRefProcEnabled",
        "-XX:+PerfDisableSharedMem"
    )
}

spotless {
    kotlin {
        ktlint("1.8.0")
        target("src/**/*.kt")
        targetExclude("**/build/**", "**/generated/**")
    }
}

// detekt 2.x (still alpha): 1.23.x can't run inside a Java 25+ Gradle daemon.
// detekt must run with the Kotlin version it was compiled against, not the project's
configurations.matching { it.name.contains("detekt", ignoreCase = true) }.configureEach {
    resolutionStrategy.eachDependency {
        if (requested.group == "org.jetbrains.kotlin") {
            useVersion("2.4.10")
        }
    }
}

detekt {
    config.setFrom(file("detekt.yml"))
    buildUponDefaultConfig = true
}

// `./gradlew detekt` runs the full-analysis task for the Gatling sources (type resolution;
// some 2.x rules only run in that mode) instead of the light-mode pass.
tasks.named("detekt") {
    enabled = false
    dependsOn("detektGatling")
}
