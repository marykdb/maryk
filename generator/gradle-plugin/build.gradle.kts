plugins {
    `java-gradle-plugin`
    id("maryk.conventions.kotlin-jvm")
    id("maryk.conventions.publishing")
}

dependencies {
    implementation(projects.generator)
    implementation(libs.kotlin.gradle.plugin)
    testImplementation(gradleTestKit())
    testImplementation(kotlin("test"))
}

gradlePlugin {
    plugins {
        create("marykGenerator") {
            id = "io.maryk.generator"
            implementationClass = "maryk.generator.gradle.MarykGeneratorPlugin"
            displayName = "Maryk schema generator"
            description = "Generates Kotlin models and checks Maryk schema compatibility"
        }
    }
}
