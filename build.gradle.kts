repositories {
    mavenCentral()
    google()
}

plugins {
    id("com.android.library") version "4.1.0" apply false
}

buildscript {
    extra["kotlinVersion"] = "1.5.10"
    extra["coroutinesVersion"] = "1.5.0"
    extra["marykRocksDBVersion"] = "6.20.4"

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
    }
}
