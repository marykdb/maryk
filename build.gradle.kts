buildscript {
    val kotlinVersion = file("kotlin-version.txt").readText().trim()
    extra["kotlinVersion"] = kotlinVersion

    repositories {
        jcenter()
    }
    dependencies {
        classpath("org.jetbrains.kotlin:kotlin-gradle-plugin:$kotlinVersion")
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
