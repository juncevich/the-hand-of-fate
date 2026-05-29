plugins {
    kotlin("jvm") version "2.1.21"
    id("io.gatling.gradle") version "3.13.5.1"
}

repositories {
    mavenCentral()
}

dependencies {
    gatling("io.gatling.highcharts:gatling-charts-highcharts:3.13.5")
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
