package maryk.conventions

import org.gradle.api.GradleException
import org.gradle.testfixtures.ProjectBuilder
import java.io.File
import java.nio.file.Files
import java.security.MessageDigest
import kotlin.test.Test
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PrepareJvmNativeComparatorTest {
    @Test
    fun offlineBuildWithoutCachedHeadersRemovesStaleNativeOutput() = withTask { task, root ->
        val stale = task.outputDirectory.file("maryk/rocksdb/native/stale.dylib").get().asFile
        stale.parentFile.mkdirs()
        stale.writeText("stale native comparator")

        task.prepare()

        assertTrue(task.outputDirectory.get().asFile.isDirectory)
        assertFalse(stale.exists())
        assertTrue(task.outputDirectory.get().asFile.listFiles().orEmpty().isEmpty())
        assertFalse(root.resolve("cache").exists())
    }

    @Test
    fun requiredNativeBuildRejectsMissingOfflineHeaders() = withTask { task, _ ->
        task.requireNative.set(true)
        val error = assertFailsWith<GradleException> { task.prepare() }
        assertTrue(error.message.orEmpty().contains("a native comparator is required"))
    }

    @Test
    fun offlineBuildStillRejectsCorruptCachedHeaders() = withTask { task, root ->
        val cached = root.resolve("cache/rocksdb-test.tar.gz")
        cached.parentFile.mkdirs()
        cached.writeText("corrupt archive")

        val error = assertFailsWith<IllegalStateException> { task.prepare() }

        assertTrue(error.message.orEmpty().contains("checksum mismatch"))
    }

    @Test
    fun offlineBuildStillRejectsCorruptExplicitHeaders() = withTask { task, root ->
        val archive = root.resolve("supplied.tar.gz").apply { writeText("corrupt archive") }
        task.sourceArchive.set(archive)

        val error = assertFailsWith<IllegalStateException> { task.prepare() }

        assertTrue(error.message.orEmpty().contains("checksum mismatch"))
    }

    @Test
    fun offlineBuildStillValidatesSuppliedNativeBundles() = withTask { task, root ->
        task.suppliedArtifacts.set(root.resolve("supplied").apply { mkdirs() })

        val error = assertFailsWith<IllegalStateException> { task.prepare() }

        assertTrue(error.message.orEmpty().contains("No native comparator artifacts supplied"))
    }

    private fun withTask(block: (PrepareJvmNativeComparator, File) -> Unit) {
        val root = Files.createTempDirectory("maryk-native-comparator-test").toFile()
        try {
            val project = ProjectBuilder.builder().withProjectDir(root).build()
            val jar = root.resolve("jni.jar").apply { writeText("test JNI artifact") }
            val hash = MessageDigest.getInstance("SHA-256").digest(jar.readBytes())
                .joinToString("") { (it.toInt() and 255).toString(16).padStart(2, '0') }
            val sources = root.resolve("native").apply { mkdirs() }
            sources.resolve("rocksdb.properties").writeText(
                "rocksdb.version=test\nrocksdb.jniJarSha256=$hash\nrocksdb.sourceSha256=invalid\n",
            )
            val task = project.tasks.create("prepareComparator", PrepareJvmNativeComparator::class.java).apply {
                nativeSources.set(sources)
                repositoryRoot.set(root)
                jniJar.set(jar)
                targetPlatform.set("macos-aarch64")
                cCompiler.set("must-not-run")
                cppCompiler.set("must-not-run")
                offline.set(true)
                archiveCache.set(root.resolve("cache"))
                outputDirectory.set(root.resolve("output"))
            }
            block(task, root)
        } finally {
            root.deleteRecursively()
        }
    }
}
