repositories {
    mavenCentral()
    google()
}

plugins {
    id("com.android.library") version "7.2.1" apply false
}

buildscript {
    extra["kotlinVersion"] = "1.7.0-RC2"
    extra["coroutinesVersion"] = "1.5.2"
    extra["marykRocksDBVersion"] = "7.0.3"

    repositories {
        mavenCentral()
    }
    dependencies {
        classpath("org.jetbrains.kotlin:kotlin-gradle-plugin:${project.extra["kotlinVersion"]}")
    }
}
allprojects {
    tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile>().all {
        kotlinOptions {
            languageVersion = "1.7"
            apiVersion = "1.7"
            freeCompilerArgs += "-progressive"
            allWarningsAsErrors = true
            jvmTarget = "1.8"
        }
    }
    repositories {
        mavenCentral()
        google()
        mavenLocal()
    }
}
