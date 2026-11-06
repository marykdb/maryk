package maryk.datastore.memory

import kotlinx.coroutines.test.runTest
import maryk.core.exceptions.RequestException
import maryk.core.models.RootDataModel
import maryk.core.properties.definitions.string
import maryk.core.properties.types.Version
import maryk.core.query.pairs.with
import maryk.core.query.requests.add
import maryk.core.query.requests.scan
import maryk.datastore.test.SerializedStateBackup
import maryk.datastore.shared.DataStoreStateBackupManifest
import maryk.datastore.shared.DataStoreStateBackupOptions
import maryk.datastore.shared.StateBackupArchiveWriter
import maryk.datastore.shared.backupState
import maryk.datastore.shared.openStateBackupArchive
import maryk.datastore.shared.restoreState
import maryk.lib.exceptions.ParseException
import maryk.test.models.SimpleMarykModel
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class StateBackupArchiveTest {
    private val models = mapOf(1u to SimpleMarykModel)
    private fun store() = InMemoryDataStore.open(dataModelsById = models)
    private fun input(bytes: ByteArray): suspend (Int) -> ByteArray {
        var position = 0
        return { size ->
            val end = minOf(bytes.size, position + minOf(size, 7))
            bytes.copyOfRange(position, end).also { position = end }
        }
    }

    @Test
    fun emptyModelRegistryArchiveRoundTripHasNoRoutingModel() = runTest {
        val source = InMemoryDataStore.open(dataModelsById = emptyMap())
        val target = InMemoryDataStore.open(dataModelsById = emptyMap())
        try {
            val frames = mutableListOf<ByteArray>()
            val manifest = source.backupState(StateBackupArchiveWriter(emptyMap(), { frames += it }, {}))
            assertTrue(manifest.modelNames.isEmpty())
            val bytes = frames.reduce { a, b -> a + b }
            val restored = target.restoreState(openStateBackupArchive(emptyMap(), input(bytes)))
            assertEquals(0, restored.models)
            assertEquals(0uL, restored.records)
        } finally { source.close(); target.close() }
    }

    @Test
    fun archiveMustNotSilentlyDropFieldsUnknownToAnOlderCompatibleMajor() = runTest {
        val source = InMemoryDataStore.open(dataModelsById = mapOf(1u to EvolvedStateArchiveModel))
        val target = store()
        try {
            source.execute(EvolvedStateArchiveModel.add(EvolvedStateArchiveModel.create {
                value with "ha evolved schema"
                additionalValue with "must survive or fail"
            }))
            val frames = mutableListOf<ByteArray>()
            source.backupState(StateBackupArchiveWriter(source.dataModelsById, { frames += it }, {}))
            val bytes = frames.reduce { a, b -> a + b }
            assertFailsWith<ParseException> { target.restoreState(openStateBackupArchive(models, input(bytes))) }
            val logical = SerializedStateBackup(source)
            source.backupState(logical)
            assertFailsWith<ParseException> { target.restoreState(logical) }
            assertTrue(target.execute(SimpleMarykModel.scan(allowTableScan = true)).values.isEmpty())
        } finally { source.close(); target.close() }
    }

    @Test
    fun archiveRejectsUnregisteredOrIncompatibleModelsBeforeDecoding() = runTest {
        val source = store()
        try {
            source.execute(SimpleMarykModel.add(SimpleMarykModel.create { value with "ha schema boundary" }))
            val frames = mutableListOf<ByteArray>()
            source.backupState(StateBackupArchiveWriter(models, { frames += it }, {}))
            val bytes = frames.reduce { a, b -> a + b }
            assertFailsWith<RequestException> { openStateBackupArchive(mapOf(1u to IncompatibleStateArchiveModel), input(bytes)) }
            assertFailsWith<RequestException> { openStateBackupArchive(emptyMap(), input(bytes)) }
            val logical = SerializedStateBackup(source)
            source.backupState(logical)
            val mismatchedWriter = StateBackupArchiveWriter(mapOf(1u to IncompatibleStateArchiveModel), {}, {})
            mismatchedWriter.begin(DataStoreStateBackupManifest(listOf(SimpleMarykModel.Meta.name), mapOf(SimpleMarykModel.Meta.name to 2u.toUShort())))
            assertFailsWith<RequestException> { logical.read { mismatchedWriter.write(it) } }
            assertFailsWith<IllegalStateException> { mismatchedWriter.complete() }
        } finally { source.close() }
    }

    @Test
    fun boundedArchiveRoundTripAndSingleUseReader() = runTest {
        val source = store()
        val target = store()
        try {
            source.execute(SimpleMarykModel.add(SimpleMarykModel.create { value with "ha archive" }))
            val frames = mutableListOf<ByteArray>()
            var published = false
            val writer = StateBackupArchiveWriter(models, { frames += it }, { published = true })
            source.backupState(writer)
            assertTrue(published)
            val bytes = frames.reduce { a, b -> a + b }
            val reader = openStateBackupArchive(models, input(bytes))
            assertEquals(1uL, target.restoreState(reader).records)
            assertEquals(source.execute(SimpleMarykModel.scan(allowTableScan = true)).values,
                target.execute(SimpleMarykModel.scan(allowTableScan = true)).values)
            assertFailsWith<IllegalStateException> { reader.read {} }
            assertFailsWith<IllegalStateException> { writer.complete() }
        } finally { source.close(); target.close() }
    }

    @Test
    fun rejectsCorruptionTruncationReorderedFramesAndTrailingBytes() = runTest {
        val source = store()
        try {
            source.execute(SimpleMarykModel.add(
                SimpleMarykModel.create { value with "ha archive one" },
                SimpleMarykModel.create { value with "ha archive two" },
            ))
            val frames = mutableListOf<ByteArray>()
            source.backupState(StateBackupArchiveWriter(models, { frames += it }, {}), DataStoreStateBackupOptions(batchSize = 1u))
            val valid = frames.reduce { a, b -> a + b }
            val corrupt = valid.copyOf().also { it[frames[0].size + frames[1].size + 12] = (it[frames[0].size + frames[1].size + 12].toInt() xor 1).toByte() }
            val reordered = (listOf(frames[0], frames[1], frames[3], frames[2], frames[4])).reduce { a, b -> a + b }
            val hugeLength = valid.copyOf().also { bytes ->
                val offset = frames[0].size + 4
                bytes[offset] = 127
                repeat(3) { bytes[offset + it + 1] = (-1).toByte() }
            }
            val obsoleteMagic = valid.copyOf().also { it["MARYK-STATE-".length] = '1'.code.toByte() }
            for (invalid in listOf(obsoleteMagic, corrupt, valid.copyOf(valid.size - 1), reordered, valid + byteArrayOf(1), hugeLength)) {
                val target = store()
                try {
                    assertFailsWith<RequestException> { target.restoreState(openStateBackupArchive(models, input(invalid))) }
                } finally { target.close() }
            }
        } finally { source.close() }
    }
}

private object IncompatibleStateArchiveModel : RootDataModel<IncompatibleStateArchiveModel>(
    name = SimpleMarykModel.Meta.name,
    version = Version(2),
) {
    val value by string(1u)
}

private object EvolvedStateArchiveModel : RootDataModel<EvolvedStateArchiveModel>(
    name = SimpleMarykModel.Meta.name,
    version = Version(1, 1),
) {
    val value by string(1u)
    val additionalValue by string(2u, required = false)
}
