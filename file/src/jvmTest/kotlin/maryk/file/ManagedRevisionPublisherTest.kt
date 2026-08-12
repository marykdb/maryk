package maryk.file

import java.nio.file.Files
import kotlin.coroutines.Continuation
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.coroutines.startCoroutine
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ManagedRevisionPublisherTest {
    @Test
    fun publishesFilesAndHashManifestBeforeCurrentPointer() {
        val output = Files.createTempDirectory("managed-export-")

        val revision = publishManagedRevision(
            output.toString(),
            listOf(
                ManagedExportFile("models/Person.json", "person".encodeToByteArray()),
                ManagedExportFile("schemas/Person.yaml", "schema".encodeToByteArray()),
            ),
            revisionId = "revision-1",
        )

        assertEquals(
            "person",
            Files.readString(output.resolve(".maryk-export/revisions/revision-1/models/Person.json")),
        )
        assertEquals(
            "revision-1\n",
            Files.readString(output.resolve(".maryk-export/current")),
        )
        assertEquals(
            "38a81e87e79631e602bf5fbd307ce2fcd382b1670c585ea09032aac778a80531  models/Person.json\n" +
                "df0ad6e43880f09c90ebf95f19110178aba6890df0010ebda7485029e2b543b4  schemas/Person.yaml\n",
            Files.readString(output.resolve(".maryk-export/revisions/revision-1/manifest.sha256")),
        )
        assertEquals(output.resolve(".maryk-export/revisions/revision-1").toString(), revision.directory)
    }

    @Test
    fun rejectsTraversalAndDuplicatePathsBeforeReplacingCurrentPointer() {
        val output = Files.createTempDirectory("managed-export-")
        publishManagedRevision(
            output.toString(),
            listOf(ManagedExportFile("safe.txt", "old".encodeToByteArray())),
            revisionId = "previous",
        )

        assertFailsWith<IllegalArgumentException> {
            publishManagedRevision(
                output.toString(),
                listOf(
                    ManagedExportFile("safe.txt", "first".encodeToByteArray()),
                    ManagedExportFile("safe.txt", "duplicate".encodeToByteArray()),
                ),
                revisionId = "failed",
            )
        }
        assertFailsWith<IllegalArgumentException> {
            publishManagedRevision(
                output.toString(),
                listOf(ManagedExportFile("../outside.txt", "outside".encodeToByteArray())),
                revisionId = "traversal",
            )
        }

        assertEquals("previous\n", Files.readString(output.resolve(".maryk-export/current")))
        assertTrue(Files.notExists(output.resolve("outside.txt")))
    }

    @Test
    fun stagesStreamingWritesBeforePublishingCurrentPointer() {
        val output = Files.createTempDirectory("managed-export-")
        publishManagedRevision(
            output.toString(),
            listOf(ManagedExportFile("previous.txt", "previous".encodeToByteArray())),
            revisionId = "previous",
        )

        assertFailsWith<IllegalStateException> {
            publishManagedRevision(output.toString(), revisionId = "failed") {
                writeText("data.json", "[")
                appendText("data.json", "]")
                throw IllegalStateException("stop")
            }
        }

        assertEquals("previous\n", Files.readString(output.resolve(".maryk-export/current")))
        assertNull(File.readText(output.resolve(".maryk-export/revisions/failed/data.json").toString()))
        assertTrue(Files.list(output.resolve(".maryk-export/revisions")).use { paths ->
            paths.iterator().asSequence().none { path -> path.fileName.toString().startsWith(".staging-") }
        })
    }

    @Test
    fun rejectsDuplicateWritesAndReservedManifestPath() {
        val output = Files.createTempDirectory("managed-export-")

        assertFailsWith<IllegalArgumentException> {
            publishManagedRevision(output.toString(), revisionId = "duplicate") {
                writeText("data.json", "first")
                writeText("data.json", "second")
            }
        }
        assertFailsWith<IllegalArgumentException> {
            publishManagedRevision(output.toString(), revisionId = "manifest") {
                writeText("manifest.sha256", "forbidden")
            }
        }
        assertFailsWith<IllegalArgumentException> {
            publishManagedRevision(output.toString(), revisionId = "path") {
                path("data.json")
                path("data.json")
            }
        }

        assertNull(File.readText(output.resolve(".maryk-export/current").toString()))
    }

    @Test
    fun publishesSuspendStreamingRevision() {
        val output = Files.createTempDirectory("managed-export-")

        runSuspend {
            publishManagedRevisionStreaming(output.toString(), revisionId = "streaming") {
                writeText("data.json", "[]")
            }
        }

        assertEquals("streaming\n", Files.readString(output.resolve(".maryk-export/current")))
        assertEquals("[]", Files.readString(output.resolve(".maryk-export/revisions/streaming/data.json")))
    }

    private fun <T> runSuspend(block: suspend () -> T): T {
        var outcome: Result<T>? = null
        block.startCoroutine(object : Continuation<T> {
            override val context = EmptyCoroutineContext
            override fun resumeWith(result: Result<T>) { outcome = result }
        })
        return requireNotNull(outcome).getOrThrow()
    }
}
