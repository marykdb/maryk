package maryk.conventions

plugins {
    id("com.vanniktech.maven.publish")
}

mavenPublishing {
    publishToMavenCentral()

    signAllPublications()
}

mavenPublishing {
    coordinates(artifactId = "maryk-${project.name}")

    pom {
        name.set("Maryk")
        description.set("Maryk is a Kotlin Multiplatform library which helps you to store, query and send data in a structured way over multiple platforms. The data store stores any value with a version, so it is possible to request only the changed data or live listen for updates.")
        inceptionYear.set("2018")
        url.set("https://github.com/marykdb/maryk")
        licenses {
            license {
                name.set("The Apache License, Version 2.0")
                url.set("https://www.apache.org/licenses/LICENSE-2.0.txt")
                distribution.set("https://www.apache.org/licenses/LICENSE-2.0.txt")
            }
        }

        developers {
            developer {
                id.set("jurmous")
                name.set("Jurriaan Mous")
                url.set("https://github.com/jurmous/")
            }
        }

        scm {
            url.set("https://github.com/marykdb/maryk")
            connection.set("scm:git:git://github.com/marykdb/maryk.git")
            developerConnection.set("scm:git:ssh://git@github.com/marykdb/maryk.git")
        }
    }
}
