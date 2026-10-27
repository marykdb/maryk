package maryk.datastore.shared.migration

import kotlinx.coroutines.test.runTest
import maryk.core.models.RootDataModel
import maryk.core.properties.definitions.NumberDefinition
import maryk.core.properties.definitions.StringDefinition
import maryk.core.properties.definitions.string
import maryk.core.properties.definitions.number
import maryk.core.properties.types.numeric.Float64
import maryk.datastore.shared.encryption.AesGcmHmacSha256EncryptionProvider
import maryk.datastore.shared.encryption.FieldEncryptionContext
import maryk.datastore.shared.encryption.FieldEncryptionEnvelope
import maryk.datastore.test.legacyFloatBytes
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals

private object EncryptedFloatingModel : RootDataModel<EncryptedFloatingModel>() {
    val value by number(1u, type = Float64, sensitive = true, unique = true)
}

private object MixedEncryptedModel : RootDataModel<MixedEncryptedModel>() {
    val value by number(1u, type = Float64)
    val label by string(2u, sensitive = true, unique = true)
}

class FloatingPointStorageMigrationTest {
    private class Metadata : StorageFormatUpgradeStore {
        override val namespaceCount = 0
        override val identity = "encrypted-float"
        private val values = mutableMapOf<String, ByteArray>()
        override suspend fun get(namespace: Int, key: ByteArray) = values[key.toHexString()]
        override suspend fun scan(namespace: Int, after: ByteArray?, limit: UInt): List<StorageFormatRow> = emptyList()
        override suspend fun convert(namespace: Int, row: StorageFormatRow) = row
        override suspend fun commit(writes: List<StorageFormatWrite>) {
            writes.forEach { if (it.value == null) values.remove(it.key.toHexString()) else values[it.key.toHexString()] = it.value }
        }
    }

    @Test
    fun leavesUnaffectedCiphertextAndSensitiveUniqueTokensUntouchedWithoutAProvider() = runTest {
        val provider = AesGcmHmacSha256EncryptionProvider(ByteArray(32) { it.toByte() }, ByteArray(32) { (it + 32).toByte() })
        val record = ByteArray(16)
        val qualifier = MixedEncryptedModel.label.ref().toStorageByteArray()
        val plain = byteArrayOf(1) + StringDefinition().toStorageBytes("unchanged")
        val encrypted = FieldEncryptionEnvelope.Contextual.magic + provider.encrypt(FieldEncryptionContext(1u, record, qualifier), plain)
        val unique = qualifier + provider.deriveDeterministicToken(1u, qualifier, plain)
        val migration = FloatingPointStorageMigration(1u, MixedEncryptedModel, Metadata(), null)
        val converted = migration.convertCell(record, qualifier, encrypted)
        assertContentEquals(qualifier, converted.key)
        assertContentEquals(encrypted, converted.value)
        assertContentEquals(unique, migration.convertUnique(unique))
    }

    @Test
    fun convertsContextualCiphertextAndPersistsUniqueTokenTranslationForRestart() = runTest {
        val metadata = Metadata()
        val provider = AesGcmHmacSha256EncryptionProvider(ByteArray(32) { it.toByte() }, ByteArray(32) { (it + 32).toByte() })
        val record = ByteArray(16) { it.toByte() }
        val qualifier = EncryptedFloatingModel.value.ref().toStorageByteArray()
        val context = FieldEncryptionContext(1u, record, qualifier)
        val legacy = byteArrayOf(1) + legacyFloatBytes(-1.0)
        val current = byteArrayOf(1) + NumberDefinition(type = Float64).toStorageBytes(-1.0)
        val encrypted = FieldEncryptionEnvelope.Contextual.magic + provider.encrypt(context, legacy)
        val oldUnique = qualifier + provider.deriveDeterministicToken(1u, qualifier, legacy)
        val expectedUnique = qualifier + provider.deriveDeterministicToken(1u, qualifier, current)
        val migration = FloatingPointStorageMigration(1u, EncryptedFloatingModel, metadata, provider)
        val converted = migration.convertCell(record, qualifier, encrypted)
        assertContentEquals(qualifier, converted.key)
        assertContentEquals(current, provider.decrypt(context, converted.value, 4, converted.value.size - 4))
        // Recreate the converter, as after a checkpoint/reopen; the mapping is durable.
        val resumed = FloatingPointStorageMigration(1u, EncryptedFloatingModel, metadata, provider)
        assertContentEquals(expectedUnique, resumed.convertUnique(oldUnique))
    }

    @Test
    fun conversionPreservesDistinctNanPayloadsAndDeletionMarkers() = runTest {
        val migration = FloatingPointStorageMigration(1u, EncryptedFloatingModel, Metadata(), null)
        val qualifier = EncryptedFloatingModel.value.ref().toStorageByteArray()
        val nan = Double.fromBits(0x7ff8000000000001L)
        // Use an ordinary model for the plaintext payload case.
        assertContentEquals(byteArrayOf(0), migration.convertCell(ByteArray(16), qualifier, byteArrayOf(0)).value)
        val ordinary = FloatingPointStorageMigration(1u, PlainFloatingModel, Metadata(), null)
        val converted = ordinary.convertCell(ByteArray(16), qualifier, byteArrayOf(1) + legacyFloatBytes(nan))
        var offset = 1
        assertEquals(nan.toRawBits(), NumberDefinition(type = Float64).readStorageBytes(8) { converted.value[offset++] }.toRawBits())
    }
}

private object PlainFloatingModel : RootDataModel<PlainFloatingModel>() {
    val value by number(1u, type = Float64)
}
