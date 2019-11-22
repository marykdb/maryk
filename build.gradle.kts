buildscript {
    extra["kotlinVersion"] = "1.3.50"
    extra["coroutinesVersion"] = "1.3.2"
    extra["marykRocksDBVersion"] = "0.2.0"

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
    }
}

repositories {
    jcenter()
}
