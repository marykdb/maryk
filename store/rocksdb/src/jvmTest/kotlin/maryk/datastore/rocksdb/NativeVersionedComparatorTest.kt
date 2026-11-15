package maryk.datastore.rocksdb

import maryk.datastore.rocksdb.processors.VersionedComparator
import maryk.rocksdb.ComparatorOptions
import maryk.rocksdb.loadRocksDBLibrary
import org.rocksdb.util.Environment
import org.rocksdb.FlushOptions
import org.rocksdb.Options
import org.rocksdb.RocksDB
import java.nio.ByteBuffer
import java.nio.file.Files
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlin.test.assertFalse
import kotlin.test.assertFailsWith
import kotlin.test.assertContentEquals
import java.util.Properties

class NativeVersionedComparatorTest {
    @Test
    fun nativeComparatorSurvivesFlushAndReopenThroughTheJniEngine() {
        loadRocksDBLibrary()
        if (!RocksDBNativeComparatorLibrary.available) return
        val directory = Files.createTempDirectory("maryk-native-comparator-test-")
        try {
            NativeVersionedComparator.create(16).use { comparator ->
                val keys = (0L until 100L).map {
                    ByteBuffer.allocate(24).putLong(it % 7).putLong(it / 7).putLong(it).array()
                }
                val ordered = keys.sortedWith { a, b -> comparator.compareBytes(a, b) }
                Options().setCreateIfMissing(true).setComparator(comparator).use { options ->
                    RocksDB.open(options, directory.toString()).use { database ->
                        keys.shuffled(Random(1711)).forEach { database.put(it, it) }
                        FlushOptions().setWaitForFlush(true).use { database.flush(it) }
                    }
                    RocksDB.open(options, directory.toString()).use { database ->
                        keys.forEach { assertContentEquals(it, database.get(it)) }
                        database.newIterator().use { iterator ->
                            iterator.seekToFirst()
                            ordered.forEach { key ->
                                assertTrue(iterator.isValid)
                                assertContentEquals(key, iterator.key())
                                iterator.next()
                            }
                            assertFalse(iterator.isValid)
                            iterator.status()
                        }
                    }
                }
                // The engine calls the comparator's configuration virtual methods
                // while writing OPTIONS, including the standalone Windows overrides.
                assertTrue(Files.list(directory).use { paths ->
                    paths.filter { it.fileName.toString().startsWith("OPTIONS-") }
                        .anyMatch { Files.readString(it).contains("comparator=maryk.VersionedComparator") }
                })
            }
        } finally {
            directory.toFile().deleteRecursively()
        }
    }

    @Test
    fun windowsSelectsOnlyTheMatchingX64DllBundle() {
        assertEquals("windows-x86_64", nativeComparatorPlatform("Windows 11", "amd64"))
        assertEquals("windows-x86_64", nativeComparatorPlatform("Windows Server 2025", "x86_64"))
        assertEquals(null, nativeComparatorPlatform("Windows 11", "aarch64"))
        val manifest = Properties().apply {
            setProperty("platform", "windows-x86_64")
            setProperty("rocksdbVersion", "11.8.1")
            setProperty("jniResource", "librocksdbjni-win64.dll")
            setProperty("locatorFile", "libmarykrocksdbimage.dll")
            setProperty("comparatorFile", "libmarykrocksdbcomparator.dll")
            listOf("jniSha256", "locatorSha256", "comparatorSha256").forEach { setProperty(it, "a".repeat(64)) }
        }
        assertTrue(nativeComparatorManifestMatches(manifest, "windows-x86_64", "11.8.1"))
        manifest.setProperty("comparatorFile", "libmarykrocksdbcomparator.so")
        assertFalse(nativeComparatorManifestMatches(manifest, "windows-x86_64", "11.8.1"))
    }

    @Test
    fun activeImageIsReusedAndOrderingMatchesTheExistingComparator() {
        loadRocksDBLibrary()
        val platform = nativeComparatorPlatform(System.getProperty("os.name"), System.getProperty("os.arch"), Environment.isMuslLibc())
        val bundled = platform != null && javaClass.getResource("/maryk/rocksdb/native/$platform/manifest.properties") != null
        if (System.getenv("MARYK_REQUIRE_NATIVE_COMPARATOR") == "1") assertTrue(bundled)
        if (!bundled) {
            assertFalse(RocksDBNativeComparatorLibrary.available)
            ComparatorOptions().use { options ->
                createPlatformVersionedComparator(options, 16).use { assertIs<VersionedComparator>(it) }
            }
            return
        }
        assertTrue(RocksDBNativeComparatorLibrary.available)
        val random = Random(1711)
        for (keySize in listOf(8, 16, 32, 128)) {
            ComparatorOptions().use { options ->
                VersionedComparator(options, keySize).use { reference ->
                    createPlatformVersionedComparator(options, keySize).use { actual ->
                        assertIs<NativeVersionedComparator>(actual)
                        repeat(25000) { index ->
                            val a = random.nextBytes(random.nextInt(260))
                            val b = random.nextBytes(random.nextInt(260))
                            if (index % 3 == 0) a.copyInto(b, endIndex = minOf(a.size, b.size))
                            val expected = reference.compare(ByteBuffer.wrap(a), ByteBuffer.wrap(b))
                            assertEquals(expected.compareTo(0), actual.compareBytes(a, b).compareTo(0))
                        }
                    }
                }
            }
        }
    }

    @Test
    fun linuxLibcVariantsSelectTheirMatchingJniResources() {
        for ((arch, jni) in listOf("aarch64" to "librocksdbjni-linux-aarch64-musl.so", "amd64" to "librocksdbjni-linux64-musl.so")) {
            val platform = assertNotNull(nativeComparatorPlatform("Linux", arch, musl = true))
            assertTrue(platform.endsWith("-musl"))
            val manifest = Properties().apply {
                setProperty("platform", platform)
                setProperty("rocksdbVersion", "11.8.1")
                setProperty("jniResource", jni)
                setProperty("locatorFile", "libmarykrocksdbimage.so")
                setProperty("comparatorFile", "libmarykrocksdbcomparator.so")
                listOf("jniSha256", "locatorSha256", "comparatorSha256").forEach { setProperty(it, "a".repeat(64)) }
            }
            assertTrue(nativeComparatorManifestMatches(manifest, platform, "11.8.1"))
            manifest.setProperty("jniResource", manifest.getProperty("jniResource").replace("-musl", ""))
            assertFalse(nativeComparatorManifestMatches(manifest, platform, "11.8.1"))
        }
        assertEquals("macos-aarch64", nativeComparatorPlatform("Mac OS X", "arm64", musl = true))
        assertEquals("linux-aarch64", nativeComparatorPlatform("Linux", "arm64"))
    }

    @Test
    fun shortModelKeysRetainTheExistingComparator() {
        loadRocksDBLibrary()
        ComparatorOptions().use { options ->
            createPlatformVersionedComparator(options, 4).use { comparator ->
                assertIs<VersionedComparator>(comparator)
            }
        }
    }

    @Test
    fun mismatchedArtifactsAreRejectedBeforeLoading() {
        val platform = "macos-aarch64"
        val manifest = Properties().apply {
            setProperty("platform", platform)
            setProperty("rocksdbVersion", "11.8.1")
            setProperty("jniResource", "librocksdbjni-osx-arm64.jnilib")
            setProperty("locatorFile", "libmarykrocksdbimage.dylib")
            setProperty("comparatorFile", "libmarykrocksdbcomparator.dylib")
            listOf("jniSha256", "locatorSha256", "comparatorSha256").forEach { setProperty(it, "a".repeat(64)) }
        }
        assertTrue(nativeComparatorManifestMatches(manifest, platform, "11.8.1"))
        assertFalse(nativeComparatorManifestMatches(manifest, platform, "11.8.2"))
        assertFalse(nativeComparatorManifestMatches(manifest, "linux-x86_64", "11.8.1"))
        manifest.setProperty("comparatorFile", "../libmarykrocksdbcomparator.dylib")
        assertFalse(nativeComparatorManifestMatches(manifest, platform, "11.8.1"))
        manifest.setProperty("comparatorFile", "libmarykrocksdbcomparator.dylib")
        manifest.setProperty("jniSha256", "unverified")
        assertFalse(nativeComparatorManifestMatches(manifest, platform, "11.8.1"))
        assertEquals(null, nativeComparatorPlatform("Linux", "riscv64"))
    }

    @Test
    fun closedNativeComparatorsRejectDirectParityReads() {
        loadRocksDBLibrary()
        if (!RocksDBNativeComparatorLibrary.available) return
        val comparator = NativeVersionedComparator.create(16)
        comparator.close()
        assertFailsWith<IllegalStateException> { comparator.compareBytes(byteArrayOf(1), byteArrayOf(2)) }
    }
}
