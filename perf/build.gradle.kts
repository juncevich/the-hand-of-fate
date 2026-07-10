plugins {
    kotlin("jvm") version "2.4.0"
    id("io.gatling.gradle") version "3.15.1.1"
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
        "-XX:+UseG1GC",
        "-XX:MaxGCPauseMillis=30",
        "-XX:G1HeapRegionSize=16m",
        "-XX:InitiatingHeapOccupancyPercent=75",
        "-XX:+ParallelRefProcEnabled",
        "-XX:+PerfDisableSharedMem",
        "-XX:+AggressiveOpts"
    )
}
