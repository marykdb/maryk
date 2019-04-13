buildscript {
    extra["kotlinVersion"] = "1.3.30"
    extra["coroutinesVersion"] = "1.2.0"

    repositories {
        jcenter()
    }
    dependencies {
        classpath("org.jetbrains.kotlin:kotlin-gradle-plugin:${extra["kotlinVersion"]}")
    }
}

// For JS projects
plugins {
    id("com.moowork.node").version("1.3.1")
}

allprojects {
    repositories {
        jcenter()
    }
}

repositories {
    jcenter()
}
