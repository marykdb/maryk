package maryk.datastore.rocksdb

import org.rocksdb.RocksDB
import org.rocksdb.util.Environment
import java.io.IOException
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.security.GeneralSecurityException
import java.security.MessageDigest
import java.util.Properties

/** Optional native implementation; unsupported binaries retain the common comparator. */
internal object RocksDBNativeComparatorLibrary {
    val available: Boolean = try {
        load()
    } catch (_: UnsatisfiedLinkError) {
        false
    } catch (_: IOException) {
        false
    } catch (_: SecurityException) {
        false
    } catch (_: GeneralSecurityException) {
        false
    }

    private fun load(): Boolean {
        val platform = nativeComparatorPlatform(System.getProperty("os.name"), System.getProperty("os.arch"), Environment.isMuslLibc())
            ?: return false
        val prefix = "/maryk/rocksdb/native/$platform/"
        val manifest = resource(prefix + "manifest.properties")?.use { input ->
            Properties().apply { load(input) }
        } ?: return false
        if (!nativeComparatorManifestMatches(manifest, platform, RocksDB.rocksdbVersion().toString())) return false

        val bundle = Files.createTempDirectory("maryk-rocksdb-comparator-")
        bundle.toFile().deleteOnExit()
        val locator = extract(bundle, prefix, manifest.getProperty("locatorFile"), manifest.getProperty("locatorSha256"))
            ?: return false
        System.load(locator.toString())
        val windows = platform == "windows-x86_64"
        val active = activeImage(windows) ?: return false
        if (sha256(active) != manifest.getProperty("jniSha256")) return false

        // A relative native dependency resolves to the same inode as Java's active
        // image, avoiding a second engine or changes to global symbol visibility.
        if (!windows) {
            val link = bundle.resolve(manifest.getProperty("jniResource"))
            Files.createSymbolicLink(link, active.toAbsolutePath())
            link.toFile().deleteOnExit()
        }
        val comparator = extract(bundle, prefix, manifest.getProperty("comparatorFile"), manifest.getProperty("comparatorSha256"))
            ?: return false
        System.load(comparator.toString())
        return activeImage(windows) == active
    }

    private fun activeImage(windows: Boolean): Path? = if (windows) {
        loadedRocksDbImageWindows()?.let { Paths.get(String(it)) }
    } else {
        loadedRocksDbImage()?.let { Paths.get(String(it, StandardCharsets.UTF_8)) }
    }

    private fun extract(bundle: Path, prefix: String, name: String, hash: String): Path? {
        val target = bundle.resolve(name)
        resource(prefix + name)?.use { Files.copy(it, target) } ?: return null
        target.toFile().deleteOnExit()
        return target.takeIf { sha256(it) == hash }
    }

    private fun resource(name: String) = RocksDBNativeComparatorLibrary::class.java.getResourceAsStream(name)
    private external fun loadedRocksDbImage(): ByteArray?
    private external fun loadedRocksDbImageWindows(): CharArray?
}

internal fun nativeComparatorPlatform(os: String, arch: String, musl: Boolean = false): String? {
    val system = when (os) {
        "Mac OS X" -> "macos"
        "Linux" -> "linux"
        else -> if (os.startsWith("Windows") && arch in setOf("amd64", "x86_64")) "windows" else return null
    }
    val architecture = when (arch) {
        "aarch64", "arm64" -> "aarch64"
        "x86_64", "amd64" -> "x86_64"
        else -> return null
    }
    return "$system-$architecture" + if (system == "linux" && musl) "-musl" else ""
}

internal fun nativeComparatorManifestMatches(manifest: Properties, platform: String, version: String): Boolean {
    if (manifest.getProperty("platform") != platform || manifest.getProperty("rocksdbVersion") != version) return false
    val suffix = when {
        platform.startsWith("macos-") -> "dylib"
        platform == "windows-x86_64" -> "dll"
        else -> "so"
    }
    if (manifest.getProperty("locatorFile") != "libmarykrocksdbimage.$suffix" ||
        manifest.getProperty("comparatorFile") != "libmarykrocksdbcomparator.$suffix"
    ) return false
    val jniName = when (platform) {
        "macos-aarch64" -> "librocksdbjni-osx-arm64.jnilib"
        "macos-x86_64" -> "librocksdbjni-osx-x86_64.jnilib"
        "linux-aarch64" -> "librocksdbjni-linux-aarch64.so"
        "linux-x86_64" -> "librocksdbjni-linux64.so"
        "linux-aarch64-musl" -> "librocksdbjni-linux-aarch64-musl.so"
        "linux-x86_64-musl" -> "librocksdbjni-linux64-musl.so"
        "windows-x86_64" -> "librocksdbjni-win64.dll"
        else -> return false
    }
    if (manifest.getProperty("jniResource") != jniName) return false
    val hashPattern = Regex("[a-f0-9]{64}")
    return listOf("jniSha256", "locatorSha256", "comparatorSha256").all {
        manifest.getProperty(it)?.matches(hashPattern) == true
    }
}

private fun sha256(path: Path): String {
    val digest = MessageDigest.getInstance("SHA-256")
    Files.newInputStream(path).use { input ->
        val buffer = ByteArray(65536)
        while (true) {
            val size = input.read(buffer)
            if (size < 0) break
            digest.update(buffer, 0, size)
        }
    }
    return digest.digest().joinToString("") { (it.toInt() and 255).toString(16).padStart(2, '0') }
}
