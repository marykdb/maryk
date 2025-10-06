plugins {
    id("maryk.conventions.kotlin-jvm")
    kotlin("plugin.compose")
    application
}

dependencies {
    implementation(projects.core)
    implementation(projects.store.shared)
    implementation(projects.store.rocksdb)
    implementation(projects.store.foundationdb)

    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:_")
    implementation("com.jakewharton.mosaic:mosaic-runtime:_")
    implementation("org.jetbrains.compose.runtime:runtime:_")
    implementation("org.jetbrains.androidx.lifecycle:lifecycle-runtime-compose:_")
    implementation("org.foundationdb:fdb-java:_")
    implementation("org.rocksdb:rocksdbjni:_")
}

application {
    mainClass.set("maryk.datastore.terminal.TerminalClientKt")
}
