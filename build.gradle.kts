plugins {
    id("com.jfrog.bintray").version("1.8.4")
}

buildscript {
    extra["kotlinVersion"] = "1.3.60"
    extra["coroutinesVersion"] = "1.3.2-1.3.60"
    extra["marykRocksDBVersion"] = "0.3.2"

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
        maven("https://kotlin.bintray.com/kotlinx")
    }
}

repositories {
    jcenter()
    maven("https://kotlin.bintray.com/kotlinx")
}
