package maryk.conventions

plugins {
    id("maryk.conventions.base")
    kotlin("jvm")
}

kotlin {
    jvmToolchain(17)
}

// configure all Tests to use JUnit Jupiter
tasks.withType<Test>().configureEach {
    useJUnitPlatform()
}
