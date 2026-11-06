package maryk.datastore.memory

import kotlinx.coroutines.test.runTest
import maryk.core.exceptions.RequestException
import maryk.core.models.IsRootDataModel
import maryk.core.properties.types.Bytes
import maryk.core.properties.types.Key
import maryk.core.query.pairs.with
import maryk.core.query.requests.add
import maryk.core.query.requests.change
import maryk.core.query.requests.scan
import maryk.core.query.changes.Change
import maryk.core.query.changes.change
import maryk.datastore.shared.DataStoreStateBackupChunk
import maryk.datastore.shared.DataStoreStateBackupManifest
import maryk.datastore.shared.DataStoreStateBackupOptions
import maryk.datastore.shared.DataStoreStateBackupReader
import maryk.datastore.shared.DataStoreStateBackupWriter
import maryk.datastore.shared.backupState
import maryk.datastore.shared.restoreState
import maryk.datastore.test.SerializedStateBackup
import maryk.datastore.test.StateAllocationDetails
import maryk.datastore.test.StateAllocationRecord
import kotlin.test.Test
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class StateBackupAllocationValidationTest {
    private val models = mapOf(1u to StateAllocationRecord)

    @Test
    fun malformedOrUnaddressedAllocationMetadataFailsBeforeWriting() = runTest {
        val source = InMemoryDataStore.open(dataModelsById = models)
        try {
            source.execute(StateAllocationRecord.add(StateAllocationRecord.create { entries with emptyMap() }))
            val chunks = mutableListOf<DataStoreStateBackupChunk>()
            var capturedManifest: DataStoreStateBackupManifest? = null
            source.backupState(object : DataStoreStateBackupWriter {
                override suspend fun begin(manifest: DataStoreStateBackupManifest) { capturedManifest = manifest }
                override suspend fun write(chunk: DataStoreStateBackupChunk) { chunks += chunk }
                override suspend fun complete() = Unit
            })
            val chunk = chunks.single()
            val key = chunk.records.single().key
            val validReference = Bytes(StateAllocationRecord.ref { entries }.toStorageByteArray())
            val validMaximum = Bytes(byteArrayOf(0, 0, 0, 9))
            val invalid = listOf(
                mapOf(key to mapOf(Bytes(StateAllocationRecord.ref { details }.toStorageByteArray()) to validMaximum)),
                mapOf(key to mapOf(validReference to Bytes(byteArrayOf(9)))),
                mapOf(Key<IsRootDataModel>(ByteArray(key.size)) to mapOf(validReference to validMaximum)),
            )
            for (allocations in invalid) {
                val target = InMemoryDataStore.open(dataModelsById = models)
                try {
                    val reader = object : DataStoreStateBackupReader {
                        override val manifest = requireNotNull(capturedManifest)
                        override suspend fun read(consumer: suspend (DataStoreStateBackupChunk) -> Unit) {
                            consumer(chunk.copy(allocations = allocations))
                        }
                    }
                    assertFailsWith<RequestException> { target.restoreState(reader) }
                    assertTrue(target.execute(StateAllocationRecord.scan(allowTableScan = true)).values.isEmpty())
                } finally { target.close() }
            }
        } finally { source.close() }
    }

    @Test
    fun allocationMetadataCountsAgainstTheRecordByteLimit() = runTest {
        val source = InMemoryDataStore.open(dataModelsById = models)
        try {
            source.execute(StateAllocationRecord.add(StateAllocationRecord.create {
                entries with mapOf(9u to "deleted root")
                details with StateAllocationDetails.create { entries with mapOf(99u to "deleted nested") }
            }))
            val key = source.execute(StateAllocationRecord.scan(allowTableScan = true)).values.single().key
            source.execute(StateAllocationRecord.change(key.change(Change(
                StateAllocationRecord.ref { entries } with emptyMap<UInt, String>(),
                StateAllocationRecord.ref { details } with null,
            ))))
            val fixture = SerializedStateBackup(source)
            source.backupState(fixture)
            val recordBytes = fixture.chunks.single().second.single().size
            assertTrue(recordBytes > 0)
            val limited = SerializedStateBackup(source)
            assertFailsWith<RequestException> { source.backupState(limited, DataStoreStateBackupOptions(maxChunkBytes = recordBytes + 4)) }
            assertFalse(limited.completed)
            assertTrue(limited.chunks.isEmpty())
        } finally { source.close() }
    }
}
