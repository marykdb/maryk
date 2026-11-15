package maryk.conventions

import org.gradle.api.GradleException
import org.gradle.api.DefaultTask
import org.gradle.api.file.ArchiveOperations
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.FileSystemOperations
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputDirectory
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.LocalState
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import org.gradle.process.ExecOperations
import org.gradle.work.DisableCachingByDefault
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.OutputStream
import java.net.URI
import java.nio.file.Files
import java.nio.file.StandardCopyOption.ATOMIC_MOVE
import java.nio.file.StandardCopyOption.REPLACE_EXISTING
import java.security.DigestOutputStream
import java.security.MessageDigest
import java.util.Properties
import java.util.zip.ZipFile
import javax.inject.Inject

/** Build a small extension against the resolved JNI image, or verify CI's exact-source bundles. */
@DisableCachingByDefault(because = "Native output depends on the host compiler and signing tools")
abstract class PrepareJvmNativeComparator @Inject constructor(
    private val exec: ExecOperations,
    private val archives: ArchiveOperations,
    private val files: FileSystemOperations,
) : DefaultTask() {
    @get:InputDirectory @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val nativeSources: DirectoryProperty
    @get:InputFiles @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val buildSources: ConfigurableFileCollection
    @get:InputFile @get:PathSensitive(PathSensitivity.NONE)
    abstract val jniJar: RegularFileProperty
    @get:InputFile @get:Optional @get:PathSensitive(PathSensitivity.NONE)
    abstract val sourceArchive: RegularFileProperty
    @get:InputDirectory @get:Optional @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val suppliedArtifacts: DirectoryProperty
    @get:Input abstract val targetPlatform: Property<String>
    @get:Input abstract val cCompiler: Property<String>
    @get:Input abstract val cppCompiler: Property<String>
    @get:Input abstract val compilerIdentity: Property<String>
    @get:Input abstract val sdkIdentity: Property<String>
    @get:InputFiles @get:PathSensitive(PathSensitivity.NONE)
    abstract val compilerBinaries: ConfigurableFileCollection
    @get:Input abstract val offline: Property<Boolean>
    @get:Input abstract val compilerArguments: ListProperty<String>
    @get:Input abstract val jniPlatform: Property<String>
    @get:Input abstract val requireNative: Property<Boolean>
    @get:InputDirectory @get:Optional @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val testArtifacts: DirectoryProperty
    @get:Input abstract val javaRuntime: Property<String>
    @get:InputFiles @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val jniHeaders: ConfigurableFileCollection
    @get:Internal abstract val javaHome: DirectoryProperty
    // Cross-builds always run; the external MSVC SDK is never shared through Gradle's cache.
    @get:Internal abstract val windowsSdk: DirectoryProperty
    @get:Internal abstract val repositoryRoot: DirectoryProperty
    @get:LocalState abstract val archiveCache: DirectoryProperty
    @get:OutputDirectory abstract val outputDirectory: DirectoryProperty

    init {
        compilerArguments.convention(emptyList())
        requireNative.convention(false)
    }

    private data class Platform(val jni: String, val family: String, val arch: String) {
        val suffix get() = when (family) { "darwin" -> "dylib"; "windows" -> "dll"; else -> "so" }
        val locator get() = "libmarykrocksdbimage.$suffix"
        val comparator get() = "libmarykrocksdbcomparator.$suffix"
    }

    @TaskAction
    fun prepare() {
        val config = properties(nativeSources.file("rocksdb.properties").get().asFile)
        val sourceHash = sourceDigest()
        val output = outputDirectory.get().asFile
        suppliedArtifacts.orNull?.asFile?.let { supplied ->
            requireDigest(jniJar.get().asFile, config.getProperty("rocksdb.jniJarSha256"))
            verify(supplied, config, sourceHash)
            files.sync { from(supplied); into(output) }
            return
        }
        testArtifacts.orNull?.asFile?.let { supplied ->
            requireDigest(jniJar.get().asFile, config.getProperty("rocksdb.jniJarSha256"))
            verify(supplied, config, sourceHash, setOf(targetPlatform.get()))
            files.sync { from(supplied); into(output) }
            return
        }
        // Remove stale native output before any fallback or failed build can leave it usable.
        files.delete { delete(output) }
        output.mkdirs()
        val platform = platforms[targetPlatform.get()]
        val reason = when {
            platform == null -> "this build host"
            platform.family == "windows" && !windowsSdk.isPresent -> "a missing MSVC Windows SDK"
            digest(jniJar.get().asFile) != config.getProperty("rocksdb.jniJarSha256") -> "this JNI artifact"
            cCompiler.get().isEmpty() || cppCompiler.get().isEmpty() -> "a missing C/C++ compiler"
            else -> null
        }
        if (reason != null) {
            useCommonComparator("Native comparator unavailable for $reason")
            return
        }
        build(checkNotNull(platform), config, sourceHash, output)
    }

    private fun useCommonComparator(reason: String) {
        if (requireNative.get()) throw GradleException("$reason; a native comparator is required")
        logger.lifecycle("$reason; using the common comparator")
    }

    private fun build(platform: Platform, config: Properties, sourceHash: String, output: File) {
        val archive = obtainArchive(config) ?: run {
            useCommonComparator("RocksDB headers are not cached for this offline build")
            return
        }
        requireDigest(archive, config.getProperty("rocksdb.sourceSha256"))
        val version = config.getProperty("rocksdb.version")
        val work = temporaryDir.resolve("native")
        files.delete { delete(work) }
        val headers = work.resolve("include")
        val licenses = output.resolve("META-INF/licenses/maryk-rocksdb-comparator")
        // Gradle's archive API handles extraction; no Python or external tar is needed.
        val tree = archives.tarTree(archive)
        files.copy {
            from(tree.matching { include("rocksdb-$version/include/**") })
            into(headers)
            eachFile { path = path.removePrefix("rocksdb-$version/include/") }
            includeEmptyDirs = false
        }
        check(headers.resolve("rocksdb/comparator.h").isFile) { "RocksDB comparator header is missing" }
        files.copy {
            from(tree.matching { licenseNames.forEach { include("rocksdb-$version/$it") } })
            into(licenses)
            eachFile { path = name }
            includeEmptyDirs = false
        }
        verifyLicenses(output, config)
        val jni = work.resolve(platform.jni)
        ZipFile(jniJar.get().asFile).use { jar ->
            jar.getInputStream(checkNotNull(jar.getEntry(platform.jni))).use { input ->
                jni.outputStream().use(input::copyTo)
            }
        }
        val destination = output.resolve("$resourcePath/${targetPlatform.get()}").apply { mkdirs() }
        val locator = destination.resolve(platform.locator)
        val comparator = destination.resolve(platform.comparator)
        val module = nativeSources.get().asFile.parentFile.parentFile
        if (platform.family == "windows") {
            buildWindows(headers, work, locator, comparator)
        } else {
            val nativeFlags = (if (platform.family == "darwin") listOf("-arch", platform.arch, "-mmacosx-version-min=11.0")
                else listOf("-fPIC", "-Wl,-z,defs")) + listOf(
                "-ffile-prefix-map=${module.absolutePath}=maryk/store/rocksdb",
                "-ffile-prefix-map=${work.absolutePath}=rocksdb-native-build",
            )
            val muslRuntime = if (targetPlatform.get().endsWith("-musl")) {
                val libcName = "libc.musl-${platform.arch}.so.1"
                val libc = File(capture(listOf(cCompiler.get()) + compilerArguments.get() + "-print-file-name=libc.so").trim())
                check(libc.isFile) { "The musl compiler must provide libc.so" }
                Files.copy(libc.toPath(), work.resolve(libcName).toPath(), REPLACE_EXISTING)
                // Some cross toolchains give libc.so no SONAME. Match Alpine's runtime
                // name so consumers do not need the musl development package.
                listOf("-Wl,--as-needed", "-L", work.path, "-l:$libcName")
            } else emptyList()
            val includes = listOf("-I", javaHome.dir("include").get().asFile.path,
                "-I", javaHome.dir("include/${jniPlatform.get()}").get().asFile.path)
            val shared = if (platform.family == "darwin") "-dynamiclib" else "-shared"
            fun installName(file: File) = if (platform.family == "darwin") listOf("-Wl,-install_name,@rpath/${file.name}") else emptyList()
            run(listOf(cCompiler.get(), "-std=c11", "-O2", shared) + compilerArguments.get() + nativeFlags + includes +
                listOf(nativeSources.file("image_locator.c").get().asFile.path, "-o", locator.path) + installName(locator) + muslRuntime +
                if (platform.family == "darwin") emptyList() else listOf("-ldl"))
            val link = if (platform.family == "darwin") listOf(jni.path)
                else listOf("-L", work.path, "-l:${platform.jni}", "-Wl,-rpath,\$ORIGIN")
            run(listOf(cppCompiler.get(), "-std=c++17", "-fno-rtti", "-O3", shared) + compilerArguments.get() + nativeFlags + includes +
                listOf("-I", headers.path, nativeSources.file("versioned_comparator.cc").get().asFile.path) + link +
                installName(comparator) + muslRuntime + listOf("-o", comparator.path))
            if (platform.family == "darwin") {
                val name = capture(listOf("otool", "-D", jni.path)).lineSequence().drop(1).first().trim()
                run(listOf("install_name_tool", "-change", name, "@loader_path/${platform.jni}", comparator.path))
                for (library in listOf(locator, comparator)) run(listOf("codesign", "--force", "--sign", "-", library.path))
            }
        }
        val manifest = expectedManifest(targetPlatform.get(), platform, config, sourceHash, digest(jni)) + mapOf(
            "locatorSha256" to digest(locator), "comparatorSha256" to digest(comparator),
        )
        destination.resolve("manifest.properties").writeText(manifest.entries.joinToString("") { "${it.key}=${it.value}\n" })
        verify(output, config, sourceHash, setOf(targetPlatform.get()))
        logger.lifecycle("Built native comparator: ${targetPlatform.get()}")
    }

    private fun buildWindows(headers: File, work: File, locator: File, comparator: File) {
        // Configurable's inline destructor otherwise emits its base vtable, whose
        // out-of-line methods the Java DLL does not export. novtable only suppresses
        // base constructor/destructor vfptr writes; member layout and virtual slots
        // stay intact. We instantiate only the concrete subclass, which overrides
        // every out-of-line virtual method and owns its complete vtable.
        val configurable = headers.resolve("rocksdb/configurable.h")
        val declaration = "class Configurable {"
        val source = configurable.readText()
        check(source.split(declaration).size == 2) { "Unexpected RocksDB Configurable declaration" }
        configurable.writeText(source.replace(declaration, "class __declspec(novtable) Configurable {"))
        val sdk = windowsSdk.get().asFile
        val includes = listOf(sdk.resolve("crt/include"), sdk.resolve("sdk/include/ucrt"),
            sdk.resolve("sdk/include/shared"), sdk.resolve("sdk/include/um"))
        val libraries = listOf(sdk.resolve("crt/lib/x86_64"), sdk.resolve("sdk/lib/ucrt/x86_64"),
            sdk.resolve("sdk/lib/um/x86_64"))
        check((includes + libraries).all(File::isDirectory)) { "Incomplete xwin MSVC SDK: $sdk" }
        val sources = nativeSources.get().asFile
        val flags = compilerArguments.get() + listOf("--target=x86_64-pc-windows-msvc", "-shared",
            "-fms-runtime-lib=dll", "-nostdlib", "-O3", "-DNDEBUG", "-D_ITERATOR_DEBUG_LEVEL=0", "-nostdinc++") +
            includes.flatMap { listOf("-isystem", it.path) } +
            listOf("-I", sources.resolve("jni-windows").path,
                "-I", javaHome.dir("include").get().asFile.path, "-I", headers.path) +
            libraries.map { "-Wl,/libpath:${it.path}" } +
            listOf("-ffile-prefix-map=${work.absolutePath}=rocksdb-native-build")
        run(listOf(cCompiler.get(), "-std=c11") + flags +
            listOf(sources.resolve("image_locator_windows.c").path, "-o", locator.path,
                "-Wl,/implib:${work.resolve("locator.lib").path}"))
        // /MD shares Microsoft's runtime with the unmodified JNI DLL. There is
        // deliberately no RocksDB library on this link line and no MinGW ABI.
        run(listOf(cppCompiler.get(), "-std=c++20", "-fno-rtti") + flags +
            listOf(sources.resolve("versioned_comparator.cc").path, "-o", comparator.path,
                "-Wl,/implib:${work.resolve("comparator.lib").path}"))
    }

    private fun verify(supplied: File, config: Properties, sourceHash: String, required: Set<String> = requiredPlatforms) {
        val base = supplied.resolve(resourcePath)
        check(base.isDirectory) { "No native comparator artifacts supplied" }
        val found = mutableSetOf<String>()
        ZipFile(jniJar.get().asFile).use { jar ->
            for (directory in base.listFiles().orEmpty().sortedBy { it.name }) {
                val platform = platforms[directory.name]
                check(directory.isDirectory && platform != null) { "Unexpected native platform artifact: $directory" }
                val manifest = properties(directory.resolve("manifest.properties"))
                val jniHash = jar.getInputStream(checkNotNull(jar.getEntry(platform.jni))).use { input -> hash { input.copyTo(it) } }
                for ((key, value) in expectedManifest(directory.name, platform, config, sourceHash, jniHash)) {
                    check(manifest.getProperty(key) == value) { "Native artifact does not match current sources: ${directory.name}/$key" }
                }
                requireDigest(directory.resolve(platform.locator), manifest.getProperty("locatorSha256"))
                requireDigest(directory.resolve(platform.comparator), manifest.getProperty("comparatorSha256"))
                found += directory.name
            }
        }
        check(found.containsAll(required)) { "Missing native platforms: ${(required - found).sorted().joinToString()}" }
        verifyLicenses(supplied, config)
        logger.lifecycle("Verified native comparator artifacts: ${found.sorted().joinToString()}")
    }

    private fun expectedManifest(target: String, platform: Platform, config: Properties, sourceHash: String, jniHash: String) = linkedMapOf(
        "platform" to target,
        "rocksdbVersion" to config.getProperty("rocksdb.version"),
        "jniJarSha256" to config.getProperty("rocksdb.jniJarSha256"),
        "sourceArchiveSha256" to config.getProperty("rocksdb.sourceSha256"),
        "sourceInputSha256" to sourceHash,
        "jniResource" to platform.jni,
        "jniSha256" to jniHash,
        "locatorFile" to platform.locator,
        "comparatorFile" to platform.comparator,
    )

    private fun verifyLicenses(root: File, config: Properties) {
        for (name in licenseNames) requireDigest(root.resolve("META-INF/licenses/maryk-rocksdb-comparator/$name"),
            config.getProperty("rocksdb.licenseSha256.$name"))
    }

    private fun obtainArchive(config: Properties): File? {
        sourceArchive.orNull?.asFile?.let { return it }
        val cached = archiveCache.file("rocksdb-${config.getProperty("rocksdb.version")}.tar.gz").get().asFile
        if (cached.isFile) return cached
        if (offline.get()) return null
        cached.parentFile.mkdirs()
        val temporary = Files.createTempFile(cached.parentFile.toPath(), "rocksdb-", ".download")
        try {
            val connection = URI("https://github.com/facebook/rocksdb/archive/refs/tags/v${config.getProperty("rocksdb.version")}.tar.gz").toURL().openConnection()
            connection.connectTimeout = 60_000
            connection.readTimeout = 60_000
            connection.getInputStream().use { input -> Files.newOutputStream(temporary).use(input::copyTo) }
            requireDigest(temporary.toFile(), config.getProperty("rocksdb.sourceSha256"))
            Files.move(temporary, cached.toPath(), ATOMIC_MOVE, REPLACE_EXISTING)
        } finally { Files.deleteIfExists(temporary) }
        return cached
    }

    private fun sourceDigest(): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val root = repositoryRoot.get().asFile
        val sources = nativeSources.get().asFile.walkTopDown().filter { it.isFile }.toList() + buildSources.files
        for (file in sources.sortedBy { it.relativeTo(root).invariantSeparatorsPath }) {
            digest.update((file.relativeTo(root).invariantSeparatorsPath + "\u0000").toByteArray())
            digest.update(file.readBytes())
        }
        return digest.digest().toHex()
    }

    private fun properties(file: File) = Properties().apply { file.inputStream().use { load(it) } }
    private fun requireDigest(file: File, expected: String?) {
        check(expected != null && digest(file) == expected) { "Native artifact checksum mismatch: $file" }
    }
    private fun digest(file: File): String = file.inputStream().use { input -> hash { input.copyTo(it) } }
    private fun hash(write: (OutputStream) -> Unit): String {
        val digest = MessageDigest.getInstance("SHA-256")
        DigestOutputStream(OutputStream.nullOutputStream(), digest).use(write)
        return digest.digest().toHex()
    }
    private fun ByteArray.toHex() = joinToString("") { "%02x".format(it.toInt() and 255) }
    private fun run(command: List<String>) { exec.exec { commandLine(command) }.assertNormalExitValue() }
    private fun capture(command: List<String>): String = ByteArrayOutputStream().also { output ->
        exec.exec { commandLine(command); standardOutput = output }.assertNormalExitValue()
    }.toString(Charsets.UTF_8)

    private companion object {
        const val resourcePath = "maryk/rocksdb/native"
        val licenseNames = listOf("LICENSE.Apache", "LICENSE.leveldb", "COPYING", "AUTHORS")
        val requiredPlatforms = setOf("macos-aarch64", "macos-x86_64", "linux-x86_64", "linux-aarch64",
            "linux-x86_64-musl", "linux-aarch64-musl", "windows-x86_64")
        val platforms = mapOf(
            "macos-aarch64" to Platform("librocksdbjni-osx-arm64.jnilib", "darwin", "arm64"),
            "macos-x86_64" to Platform("librocksdbjni-osx-x86_64.jnilib", "darwin", "x86_64"),
            "linux-aarch64" to Platform("librocksdbjni-linux-aarch64.so", "linux", "aarch64"),
            "linux-aarch64-musl" to Platform("librocksdbjni-linux-aarch64-musl.so", "linux", "aarch64"),
            "linux-x86_64-musl" to Platform("librocksdbjni-linux64-musl.so", "linux", "x86_64"),
            "linux-x86_64" to Platform("librocksdbjni-linux64.so", "linux", "x86_64"),
            "windows-x86_64" to Platform("librocksdbjni-win64.dll", "windows", "x86_64"),
        )
    }
}
