repositories {
    google()
    jcenter()
}

plugins {
    id("com.android.library") version "3.6.0" apply false
}

buildscript {
    extra["kotlinVersion"] = "1.3.72"
    extra["coroutinesVersion"] = "1.3.7"
    extra["marykRocksDBVersion"] = "0.6.2"

    repositories {
        jcenter()
    }
    dependencies {
        classpath("org.jetbrains.kotlin:kotlin-gradle-plugin:${project.extra["kotlinVersion"]}")
    }
}

allprojects {
    repositories {
        jcenter()
        google()
    }
}
