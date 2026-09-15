import io.gitlab.arturbosch.detekt.Detekt

plugins {
    kotlin("jvm") version "2.4.20"
    id("io.gatling.gradle") version "3.15.1.3"
    id("com.diffplug.spotless") version "8.10.2"
    id("io.gitlab.arturbosch.detekt") version "1.23.8"
}

repositories {
    mavenCentral()
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

// detekt 1.23.x was compiled with Kotlin 2.0.21; pin its classpath to avoid version mismatch
configurations.matching { it.name.contains("detekt", ignoreCase = true) }.configureEach {
    resolutionStrategy.eachDependency {
        if (requested.group == "org.jetbrains.kotlin") {
            useVersion("2.0.21")
        }
    }
}

detekt {
    config.setFrom(file("detekt.yml"))
    buildUponDefaultConfig = true
    source.setFrom("src/gatling/kotlin")
}

// detekt's bundled IntelliJ runtime doesn't handle Java 26+ — run against a Java 17 JDK home
val detektJdkHome = javaToolchains.launcherFor { languageVersion.set(JavaLanguageVersion.of(17)) }
    .map { it.metadata.installationPath }
// detekt 1.23.x only supports --jvm-target up to 22; cap it regardless of the project toolchain
tasks.withType<Detekt>().configureEach {
    jvmTarget = "22"
    jdkHome.set(detektJdkHome)
}
