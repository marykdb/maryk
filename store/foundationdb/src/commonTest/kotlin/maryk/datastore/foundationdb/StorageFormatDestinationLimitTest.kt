package maryk.datastore.foundationdb

import kotlinx.coroutines.test.runTest
import maryk.core.models.RootDataModel
import maryk.core.models.migration.MigrationException
import maryk.core.models.migration.MigrationPhase
import maryk.core.models.migration.MigrationState
import maryk.core.extensions.bytes.toVarBytes
import maryk.core.properties.definitions.MapDefinition
import maryk.core.properties.definitions.NumberDefinition
import maryk.core.properties.definitions.StringDefinition
import maryk.core.properties.definitions.map
import maryk.core.properties.definitions.number
import maryk.core.properties.types.numeric.Float64
import maryk.core.query.pairs.with
import maryk.core.query.requests.add
import maryk.core.query.responses.statuses.AddSuccess
import maryk.datastore.foundationdb.processors.helpers.awaitResult
import maryk.datastore.foundationdb.processors.helpers.encodeZeroFreeUsing01
import maryk.datastore.foundationdb.processors.helpers.nextBlocking
import maryk.datastore.shared.encryption.AesGcmHmacSha256EncryptionProvider
import maryk.datastore.shared.encryption.ContextualFieldEncryptionProvider
import maryk.datastore.shared.encryption.FieldEncryptionContext
import maryk.datastore.shared.encryption.FieldEncryptionEnvelope
import maryk.datastore.shared.migration.StorageFormatMetadataKey
import maryk.datastore.shared.migration.StorageFormatUpgradeStateKey
import maryk.datastore.test.legacyFloatBytes
import maryk.foundationdb.Range
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

private object CipherLimitRecord : RootDataModel<CipherLimitRecord>() {
    val value by number(1u, type = Float64, sensitive = true)
}

private object TableKeyLimitRecord : RootDataModel<TableKeyLimitRecord>() {
    val entries by map(1u, keyDefinition = NumberDefinition(type = Float64),
        valueDefinition = MapDefinition(keyDefinition = StringDefinition(), valueDefinition = StringDefinition()))
}

private object UniqueKeyLimitRecord : RootDataModel<UniqueKeyLimitRecord>() {
    val entries by map(1u, keyDefinition = StringDefinition(), valueDefinition = NumberDefinition(type = Float64, unique = true))
}

/** Valid reversible contextual encoding with padding that exceeds FDB's value ceiling. */
private class ExpandingProvider(private val delegate: ContextualFieldEncryptionProvider) : ContextualFieldEncryptionProvider by delegate {
    override suspend fun encrypt(context: FieldEncryptionContext, value: ByteArray, offset: Int, length: Int): ByteArray =
        delegate.encrypt(context, value, offset, length) + ByteArray(100_001)
    override suspend fun decrypt(context: FieldEncryptionContext, value: ByteArray, offset: Int, length: Int): ByteArray =
        delegate.decrypt(context, value, offset, if (length > 100_001) length - 100_001 else length)
}

class StorageFormatDestinationLimitTest {
    private fun snapshot(store: FoundationDBDataStore, prefix: ByteArray) = store.tc.run { transaction ->
        val iterator = transaction.getRange(Range.startsWith(prefix)).iterator()
        buildMap {
            while (iterator.hasNext()) {
                val row = iterator.nextBlocking()
                put(row.key.toHexString(), row.value.toHexString())
            }
        }
    }

    private fun assertNeverReachedBackfill(store: FoundationDBDataStore) {
        store.tc.run { transaction ->
            assertNull(transaction.get(store.storageFormatPrefix + StorageFormatMetadataKey).awaitResult())
            transaction.get(store.storageFormatPrefix + StorageFormatUpgradeStateKey).awaitResult()?.let {
                assertEquals(MigrationPhase.Expand, MigrationState.requireFromPersistedBytes(it).phase)
            }
        }
    }

    @Test
    fun expandedContextualCiphertextFailsBeforeRemovingAnySourceCell() = runTest {
        val provider = AesGcmHmacSha256EncryptionProvider(ByteArray(32) { it.toByte() }, ByteArray(32) { (it + 32).toByte() })
        val directory = listOf("maryk", "cipher-destination-limit-${Random.nextInt()}")
        val source = FoundationDBDataStore.open(fdbClusterFilePath = "./fdb.cluster", directoryPath = directory,
            dataModelsById = mapOf(1u to CipherLimitRecord), keepAllVersions = true, fieldEncryptionProvider = provider)
        try {
            val added = assertIs<AddSuccess<CipherLimitRecord>>(source.execute(CipherLimitRecord.add(
                CipherLimitRecord.create { value with -1.0 },
            )).statuses.single())
            val dirs = source.getTableDirs(1u) as HistoricTableDirectories
            val qualifier = CipherLimitRecord.value.ref().toStorageByteArray()
            val context = FieldEncryptionContext(1u, added.key.bytes, qualifier)
            val encrypted = FieldEncryptionEnvelope.Contextual.magic + provider.encrypt(context, byteArrayOf(1) + legacyFloatBytes(-1.0))
            source.tc.run { transaction ->
                val key = dirs.tablePrefix + added.key.bytes + qualifier
                val current = assertNotNull(transaction.get(key).awaitResult())
                transaction.set(key, current.copyOfRange(0, 8) + encrypted)
                val history = transaction.getRange(Range.startsWith(dirs.historicTablePrefix + added.key.bytes)).iterator()
                while (history.hasNext()) {
                    val row = history.nextBlocking()
                    if (row.key.size > dirs.historicTablePrefix.size + added.key.bytes.size) transaction.set(row.key, encrypted)
                }
                transaction.clear(source.storageFormatPrefix + StorageFormatMetadataKey)
            }
            val before = listOf(dirs.tablePrefix, dirs.historicTablePrefix).map { snapshot(source, it) }
            val failure = assertFailsWith<MigrationException> {
                FoundationDBDataStore.open(fdbClusterFilePath = "./fdb.cluster", directoryPath = directory,
                    dataModelsById = mapOf(1u to CipherLimitRecord), keepAllVersions = true,
                    fieldEncryptionProvider = ExpandingProvider(provider)).close()
            }
            assertTrue(failure.message.orEmpty().contains("valueBytes="))
            assertEquals(before, listOf(dirs.tablePrefix, dirs.historicTablePrefix).map { snapshot(source, it) })
            assertNeverReachedBackfill(source)
        } finally { source.close() }
    }

    @Test
    fun zeroFreeHistoricTableAndUniqueKeyExpansionFailsBeforeSourceRemoval() = runTest {
        for (unique in listOf(false, true)) {
            val model = if (unique) UniqueKeyLimitRecord else TableKeyLimitRecord
            val source = FoundationDBDataStore.open(fdbClusterFilePath = "./fdb.cluster",
                directoryPath = listOf("maryk", "key-destination-limit-$unique-${Random.nextInt()}"),
                dataModelsById = mapOf(1u to model), keepAllVersions = true)
            try {
                val dirs = source.getTableDirs(1u) as HistoricTableDirectories
                val prefix = if (unique) dirs.historicUniquePrefix else dirs.historicTablePrefix
                val value = Double.fromBits(0x3ff0000000000001L)
                val oldNumber = legacyFloatBytes(value)
                val newNumber = NumberDefinition(type = Float64).toStorageBytes(value)
                val record = ByteArray(model.Meta.keyByteSize) { 7 }
                val version = ByteArray(9) { if (it == 0) 0 else -1 }
                var oldKey: ByteArray? = null
                var newKey: ByteArray? = null
                for (size in 9_800..10_000) {
                    val text = ByteArray(size) { 97 }
                    val oldContent: ByteArray
                    val currentContent: ByteArray
                    if (unique) {
                        val qualifier = UniqueKeyLimitRecord.entries.ref().toStorageByteArray() + size.toVarBytes() + text
                        oldContent = qualifier + byteArrayOf(1) + oldNumber
                        currentContent = qualifier + byteArrayOf(1) + newNumber
                    } else {
                        val qualifier = TableKeyLimitRecord.entries.ref().toStorageByteArray()
                        oldContent = qualifier + byteArrayOf(8) + oldNumber + byteArrayOf(4) + size.toVarBytes() + text
                        currentContent = qualifier + byteArrayOf(8) + newNumber + byteArrayOf(4) + size.toVarBytes() + text
                    }
                    val owner = if (unique) byteArrayOf() else record
                    val candidate = prefix + owner + encodeZeroFreeUsing01(oldContent) + version
                    val converted = prefix + owner + encodeZeroFreeUsing01(currentContent) + version
                    if (candidate.size <= 10_000 && converted.size > 10_000) { oldKey = candidate; newKey = converted; break }
                }
                val key = assertNotNull(oldKey)
                assertTrue(assertNotNull(newKey).size > 10_000)
                val payload = if (unique) record else byteArrayOf(1, 120)
                source.tc.run { transaction ->
                    transaction.set(key, payload)
                    transaction.clear(source.storageFormatPrefix + StorageFormatMetadataKey)
                }
                val before = snapshot(source, prefix)
                val failure = assertFailsWith<MigrationException> { source.upgradeFoundationDBStorageFormat(preparePublication = {}) }
                assertTrue(failure.message.orEmpty().contains("keyBytes="))
                assertEquals(before, snapshot(source, prefix))
                source.tc.run { assertContentEquals(payload, it.get(key).awaitResult()) }
                assertNeverReachedBackfill(source)
            } finally { source.close() }
        }
    }
}
