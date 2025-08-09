plugins {
    `kotlin-dsl`
}

dependencies {
    implementation(libs.kotlin.gradle.plugin)
    implementation(libs.android.gradle.plugin)
    implementation("com.vanniktech.maven.publish:com.vanniktech.maven.publish.gradle.plugin:0.33.0")
}

kotlin {
    jvmToolchain(17)
}
