plugins {
    kotlin("multiplatform")
}

apply {
    from("../../gradle/common.gradle")
    from("../../gradle/jvm.gradle")
}

val coroutinesVersion = rootProject.extra["coroutinesVersion"]
val marykRocksDBVersion = rootProject.extra["marykRocksDBVersion"]

repositories {
    maven {
        setUrl("https://dl.bintray.com/maryk/maven")
    }
    jcenter()
}

kotlin {
    sourceSets {
        commonMain {
            dependencies {
                implementation ("org.jetbrains.kotlinx:kotlinx-coroutines-core:$coroutinesVersion")
                api("org.jetbrains.kotlinx:kotlinx-coroutines-core-common:$coroutinesVersion")
                api("io.maryk.rocksdb:rocksdb:$marykRocksDBVersion")

                api(project(":store-shared"))
            }
        }
        commonTest {
            dependencies {
                api(project(":testmodels"))
                api(project(":store-test"))
            }
        }
        jvm().compilations["main"].defaultSourceSet {
            dependencies {
                api("org.jetbrains.kotlinx:kotlinx-coroutines-jdk8:$coroutinesVersion")
            }
        }
    }
}

// Creates the folders for the database
val createOrEraseDBFolders = task("createOrEraseDBFolders") {
    group = "verification"

    val subdir = File(project.buildDir, "test-database")

    if (!subdir.exists()) {
        subdir.deleteOnExit()
        subdir.mkdirs()
    } else {
        subdir.deleteRecursively()
        subdir.mkdirs()
    }
}

tasks.withType<Test> {
    this.dependsOn(createOrEraseDBFolders)
    this.doLast {
        File(project.buildDir, "test-database").deleteRecursively()
    }
}
