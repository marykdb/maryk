package maryk.conventions

plugins {
    id("maryk.conventions.base")
    kotlin("jvm")
}

kotlin {
    jvmToolchain(21)
}

// configure all Tests to use JUnit Jupiter
tasks.withType<Test>().configureEach {
    useJUnitPlatform()
}
