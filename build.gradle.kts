repositories {
    mavenCentral()
    google()
}

plugins {
    id("com.android.library") version "4.2.2" apply false
}

buildscript {
    extra["kotlinVersion"] = "1.6.10"
    extra["coroutinesVersion"] = "1.6.0"
    extra["marykRocksDBVersion"] = "6.25.3"

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
