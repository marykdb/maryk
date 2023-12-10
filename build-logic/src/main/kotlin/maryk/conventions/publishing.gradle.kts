package maryk.conventions

import org.gradle.api.plugins.JavaBasePlugin.DOCUMENTATION_GROUP

/**
 * Conventions for publishing.
 *
 * Mostly focused on Maven Central publishing, which requires
 *
 * * a Javadoc JAR (even if the project is not a Java project)
 * * artifacts are signed (and Gradle's [SigningPlugin] is outdated and does not have good support for lazy config/caching)
 */

plugins {
    signing
    `maven-publish`
}


//region Publication Properties
// can be set in `$GRADLE_USER_HOME/gradle.properties`, e.g. `maryk_ossrhPassword=123`
// or environment variables, e.g. `ORG_GRADLE_PROJECT_maryk_ossrhUsername=abc`
val ossrhUsername = providers.gradleProperty("maryk_ossrhUsername")
val ossrhPassword = providers.gradleProperty("maryk_ossrhPassword")

val signingKey = providers.gradleProperty("maryk_signing_key")
val signingPassword = providers.gradleProperty("maryk_signing_password")

val isReleaseVersion = provider { !version.toString().endsWith("-SNAPSHOT") }

val sonatypeReleaseUrl = isReleaseVersion.map { isRelease ->
    if (isRelease) {
        "https://s01.oss.sonatype.org/service/local/staging/deploy/maven2/"
    } else {
        "https://s01.oss.sonatype.org/content/repositories/snapshots/"
    }
}
//endregion


//region POM convention
publishing {
    publications.withType<MavenPublication>().configureEach {
        pom {
            artifactId = "maryk-${artifactId}"
            name.convention("Maryk")
            description.convention("Maryk is a Kotlin Multiplatform library which helps you to store, query and send data in a structured way over multiple platforms. The data store stores any value with a version, so it is possible to request only the changed data or live listen for updates.")
            url.convention("https://github.com/marykdb/maryk")

            scm {
                connection.convention("scm:git:git@github.com:marykdb/maryk.git")
                developerConnection.convention("scm:git:git@github.com:marykdb/maryk.git")
                url.convention("https://github.com/marykdb/maryk/")
            }

            licenses {
                license {
                    name.set("The Apache Software License, Version 2.0")
                    url.set("https://www.apache.org/licenses/LICENSE-2.0.txt")
                    distribution.set("repo")
                }
            }
            developers {
                developer {
                    id.set("jurmous")
                    name.set("Jurriaan Mous")
                }
            }

            scm {
                url.set("https://github.com/marykdb/maryk")
            }
        }
    }
}
//endregion


//region Maven Central publishing/signing
val javadocJar by tasks.registering(Jar::class) {
    // Maven Central requires a Javadoc JAR is present, so create a dummy empty JAR
    group = DOCUMENTATION_GROUP
    description = "Javadoc Jar"
    archiveClassifier.set("javadoc")
}

publishing {
    repositories {
        maven(sonatypeReleaseUrl) {
            name = "SonatypeRelease"
            credentials {
                username = ossrhUsername.orNull
                password = ossrhPassword.orNull
            }
        }

        // Publish to a project-local Maven directory, for verification.
        // To test, run:
        // ./gradlew publishAllPublicationsToProjectLocalRepository
        // and check $rootDir/build/maven-project-local
        maven(rootProject.layout.buildDirectory.dir("maven-project-local")) {
            name = "ProjectLocal"
        }
    }

    publications.withType<MavenPublication>().configureEach {
        // Maven Central requires Javadoc
        artifact(javadocJar)
    }
}

signing {
    if (ossrhUsername.isPresent && ossrhPassword.isPresent) {
        logger.lifecycle("publishing.gradle.kts enabled signing for ${project.path}")
        if (signingKey.isPresent && signingPassword.isPresent) {
            useInMemoryPgpKeys(signingKey.get(), signingPassword.get())
        } else {
            useGpgCmd()
        }

        afterEvaluate {
            // Register signatures in afterEvaluate, otherwise the signing plugin creates
            // the signing tasks too early, before all the publications are added.
            signing {
                sign(publishing.publications)
            }
        }
    }
}
//endregion


//region Fix Gradle warning about signing tasks using publishing task outputs without explicit dependencies
// https://youtrack.jetbrains.com/issue/KT-46466
val signingTasks = tasks.withType<Sign>()

tasks.withType<AbstractPublishToMaven>().configureEach {
    mustRunAfter(signingTasks)
}
//endregion


//region publishing logging
tasks.withType<AbstractPublishToMaven>().configureEach {
    val publicationGAV = provider { publication?.run { "$group:$artifactId:$version" } }
    doLast("log publication GAV") {
        if (publicationGAV.isPresent) {
            logger.lifecycle("[task: ${path}] ${publicationGAV.get()}")
        }
    }
}
//endregion
