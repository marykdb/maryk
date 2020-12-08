repositories {
    google()
    jcenter()
}

plugins {
    id("com.android.library") version "4.0.1" apply false
}

buildscript {
    extra["kotlinVersion"] = "1.4.21"
    extra["coroutinesVersion"] = "1.4.2"
    extra["marykRocksDBVersion"] = "0.7"

    repositories {
        jcenter()
    }
    dependencies {
        classpath("org.jetbrains.kotlin:kotlin-gradle-plugin:${project.extra["kotlinVersion"]}")
    }
}

allprojects {
    repositories {
        mavenLocal()
        jcenter()
        google()
    }
}
