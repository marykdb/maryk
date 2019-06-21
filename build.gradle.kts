buildscript {
    extra["kotlinVersion"] = "1.3.40"
    extra["coroutinesVersion"] = "1.2.2"
    extra["marykRocksDBVersion"] = "0.1.3"

    repositories {
        jcenter()
    }
    dependencies {
        classpath("org.jetbrains.kotlin:kotlin-gradle-plugin:${extra["kotlinVersion"]}")
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
