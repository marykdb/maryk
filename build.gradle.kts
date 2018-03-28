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

val group by project
val version by project

allprojects {
    group = group
    version = version

    repositories {
        jcenter()
    }
}

repositories {
    jcenter()
}
