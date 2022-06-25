repositories {
    mavenCentral()
    google()
}

plugins {
    id("com.android.library") apply false
}

buildscript {
    repositories {
        mavenCentral()
    }
    dependencies {
        classpath("org.jetbrains.kotlin:kotlin-gradle-plugin:_")
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
