repositories {
    google()
    jcenter()
}

plugins {
    id("com.android.library") version "4.0.1" apply false
}

buildscript {
    extra["kotlinVersion"] = "1.5.0"
    extra["coroutinesVersion"] = "1.4.2"
    extra["marykRocksDBVersion"] = "0.7"

    repositories {
        jcenter()
        maven( url= "https://dl.bintray.com/kotlin/kotlin-eap" )
    }
    dependencies {
        classpath("org.jetbrains.kotlin:kotlin-gradle-plugin:${project.extra["kotlinVersion"]}")
    }
}

allprojects {
    repositories {
        maven( url= "https://dl.bintray.com/kotlin/kotlin-eap" )
        mavenLocal()
        jcenter()
        google()
    }
}
