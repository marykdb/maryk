buildscript {
    extra["kotlinVersion"] = "1.3.21"
    extra["coroutinesVersion"] = "1.1.1"

    repositories {
        jcenter()
    }
    dependencies {
        classpath("org.jetbrains.kotlin:kotlin-gradle-plugin:${extra["kotlinVersion"]}")
    }
}

// For JS projects
plugins {
    id("com.moowork.node").version("1.2.0")
}

allprojects {
    repositories {
        jcenter()
    }
}

repositories {
    jcenter()
}
