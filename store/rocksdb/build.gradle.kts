plugins {
    kotlin("multiplatform")
}

apply {
    from("../../gradle/android.gradle")
    from("../../gradle/common.gradle")
    from("../../gradle/jvm.gradle")
    from("../../gradle/native.gradle")
    from("../../gradle/publish.gradle")
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
                api("io.maryk.rocksdb:rocksdb-multiplatform:$marykRocksDBVersion")

                api(project(":store-shared"))
            }
        }
        commonTest {
            dependencies {
                api(project(":testmodels"))
                api(project(":store-test"))
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

tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile>().all {
    kotlinOptions {
        freeCompilerArgs = freeCompilerArgs + "-Xuse-experimental=kotlin.Experimental"
    }
}
