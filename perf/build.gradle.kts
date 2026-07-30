plugins {
    kotlin("jvm") version "2.4.0"
    id("io.gatling.gradle") version "3.15.1.2"
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
