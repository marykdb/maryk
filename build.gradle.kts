repositories {
    mavenCentral()
    google()
}

plugins {
    id("com.android.library") version "7.0.4" apply false
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
    repositories {
        mavenCentral()
        google()
        mavenLocal()
    }
}
