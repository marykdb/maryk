repositories {
    google()
    jcenter()
}

plugins {
    id("com.android.library") version "3.5.3" apply false
}

buildscript {
    extra["kotlinVersion"] = "1.3.70"
    extra["coroutinesVersion"] = "1.3.4"
    extra["marykRocksDBVersion"] = "0.6.0"

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
