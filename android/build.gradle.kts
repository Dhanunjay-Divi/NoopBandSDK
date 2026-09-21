plugins {
    kotlin("jvm") version "2.1.0"
    application
}

group = "com.noop.bandsdk"
version = "0.1.0"

kotlin {
    jvmToolchain(17)
}

application {
    applicationName = "noop-band-conformance"
    mainClass.set("com.noop.bandsdk.ConformanceMainKt")
}

dependencies {
    testImplementation(kotlin("test-junit"))
}
