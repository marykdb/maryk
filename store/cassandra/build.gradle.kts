plugins {
    kotlin("multiplatform")
}

apply {
    from("../../gradle/publish.gradle")
}


kotlin {
    jvm()

    sourceSets {
        val jvmMain by getting {
            dependencies {
                api(project(":store-shared"))
                implementation(libs.java.driver.core)
                implementation(libs.jna)
            }
        }
        val jvmTest by getting {
            dependencies {
                api(project(":testmodels"))
                api(project(":store-test"))
                implementation(libs.cassandra.all)
                implementation(libs.netty.tcnative.boringssl.static)
            }
        }
    }
}

tasks.named<Test>("jvmTest") {
    jvmArgs("--add-opens=java.base/jdk.internal.ref=ALL-UNNAMED")
}
