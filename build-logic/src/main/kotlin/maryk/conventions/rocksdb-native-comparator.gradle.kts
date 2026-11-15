package maryk.conventions

val requestedNativeTarget = providers.gradleProperty("maryk.rocksdbNativeTarget")
val generatedNativeResources = layout.buildDirectory.dir(requestedNativeTarget.map {
    "generated/jvmNativeComparatorCrossResources/$it"
}.orElse("generated/jvmNativeComparatorResources"))
val suppliedNativeResources = providers.gradleProperty("maryk.rocksdbNativeArtifactsDir")
val nativeHost = when (System.getProperty("os.name")) {
    "Mac OS X" -> "macos"
    "Linux" -> "linux"
    else -> if (System.getProperty("os.name").startsWith("Windows")) "windows" else "unsupported"
} + "-" + when (System.getProperty("os.arch")) {
    "aarch64", "arm64" -> "aarch64"
    "x86_64", "amd64" -> "x86_64"
    else -> "unsupported"
} + if (System.getProperty("os.name") == "Linux" &&
    File("/lib").listFiles().orEmpty().any { it.name.startsWith("ld-musl-") }) "-musl" else ""
val compilerPath = providers.environmentVariable("PATH").orElse("")
fun findNativeCompiler(command: String, path: String): String =
    (if (command.contains('/')) sequenceOf(file(command))
    else path.split(File.pathSeparator).asSequence().map { File(it, command) })
        .firstOrNull { it.isFile && it.canExecute() }?.absolutePath.orEmpty()
val resolvedCCompiler = providers.gradleProperty("maryk.rocksdbNativeCC")
    .orElse(providers.environmentVariable("CC")).orElse("cc").zip(compilerPath, ::findNativeCompiler)
val resolvedCppCompiler = providers.gradleProperty("maryk.rocksdbNativeCXX")
    .orElse(providers.environmentVariable("CXX")).orElse("c++").zip(compilerPath, ::findNativeCompiler)
fun compilerVersion(compiler: String) = if (compiler.isEmpty()) providers.provider { "unavailable" }
    else providers.exec { commandLine(compiler, "--version") }.standardOutput.asText
val compilerVersions = resolvedCCompiler.flatMap(::compilerVersion)
    .zip(resolvedCppCompiler.flatMap(::compilerVersion)) { c, cpp -> "$c\n$cpp" }
val sdkVersion = if (nativeHost.startsWith("macos-")) providers.exec {
    commandLine("xcrun", "--show-sdk-version")
}.standardOutput.asText else providers.provider { "" }
val sdkEnvironment = providers.environmentVariable("SDKROOT").orElse("")
    .zip(providers.environmentVariable("DEVELOPER_DIR").orElse("")) { sdk, developer -> "$sdk\n$developer" }

tasks.register<PrepareJvmNativeComparator>("prepareJvmNativeComparator") {
    nativeSources.set(layout.projectDirectory.dir("src/jvmNative"))
    buildSources.from(layout.projectDirectory.file("build.gradle.kts"),
        layout.projectDirectory.file("scripts/compile-jvm-comparator.sh"),
        rootProject.layout.projectDirectory.file("build-logic/src/main/kotlin/maryk/conventions/rocksdb-native-comparator.gradle.kts"),
        rootProject.layout.projectDirectory.file("build-logic/src/main/kotlin/maryk/conventions/PrepareJvmNativeComparator.kt"))
    repositoryRoot.set(rootProject.layout.projectDirectory)
    jniJar.set(layout.file(configurations.named("jvmRuntimeClasspath").map { configuration ->
        configuration.files.single { it.name.startsWith("rocksdbjni-") && it.name.matches(Regex("rocksdbjni-[0-9.]+\\.jar")) }
    }))
    sourceArchive.set(layout.file(providers.gradleProperty("maryk.rocksdbNativeSourceArchive").map { file(it) }))
    windowsSdk.set(layout.dir(providers.gradleProperty("maryk.rocksdbNativeWindowsSdk").map { file(it) }))
    suppliedArtifacts.set(layout.dir(suppliedNativeResources.map { file(it) }))
    targetPlatform.set(requestedNativeTarget.orElse(nativeHost))
    compilerArguments.set(providers.gradleProperty("maryk.rocksdbNativeCompilerArguments")
        .map { it.split(';').filter(String::isNotBlank) }.orElse(emptyList()))
    jniPlatform.set(if (nativeHost.startsWith("macos-")) "darwin" else "linux")
    requireNative.set(requestedNativeTarget.isPresent ||
        providers.environmentVariable("MARYK_REQUIRE_NATIVE_COMPARATOR").orNull == "1")
    testArtifacts.set(layout.dir(providers.gradleProperty("maryk.rocksdbNativeTestArtifactsDir").map { file(it) }))
    cCompiler.set(resolvedCCompiler)
    cppCompiler.set(resolvedCppCompiler)
    compilerIdentity.set(compilerVersions)
    sdkIdentity.set(sdkVersion.zip(sdkEnvironment) { version, environment -> "$version\n$environment" })
    compilerBinaries.from(resolvedCCompiler.map { if (it.isEmpty()) emptyList() else listOf(file(it)) },
        resolvedCppCompiler.map { if (it.isEmpty()) emptyList() else listOf(file(it)) })
    offline.set(gradle.startParameter.isOffline)
    javaHome.set(file(System.getProperty("java.home")))
    javaRuntime.set(System.getProperty("java.runtime.version"))
    jniHeaders.from(fileTree(File(System.getProperty("java.home"), "include")))
    archiveCache.set(gradle.gradleUserHomeDir.resolve("caches/maryk/rocksdb-native"))
    outputDirectory.set(generatedNativeResources)
    // Publication verifies supplied bundles on every invocation before copying them.
    // Retry fallback builds so a subsequently available archive can enable native output.
    // Explicit cross-builds also refresh external sysroot and runtime-library inputs.
    outputs.upToDateWhen {
        !requestedNativeTarget.isPresent && !windowsSdk.isPresent && !suppliedArtifacts.isPresent && !testArtifacts.isPresent &&
            generatedNativeResources.get().dir("maryk/rocksdb/native").asFile.isDirectory
    }
}
