package maryk.datastore.rocksdb

import kotlinx.coroutines.test.runTest
import maryk.core.exceptions.RequestException
import maryk.core.models.RootDataModel
import maryk.core.properties.definitions.fixedBytes
import maryk.core.properties.definitions.string
import maryk.core.properties.types.Bytes
import maryk.core.query.requests.add
import maryk.core.query.responses.statuses.AddSuccess
import maryk.core.query.responses.statuses.IsAddResponseStatus
import maryk.core.query.responses.statuses.ValidationFail
import maryk.createTestDBFolder
import maryk.datastore.shared.encryption.FieldEncryptionProvider
import maryk.datastore.shared.encryption.SensitiveIndexTokenProvider
import maryk.datastore.shared.TypeIndicator
import maryk.datastore.rocksdb.processors.helpers.VERSION_BYTE_SIZE
import maryk.deleteFolder
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull

class RocksDBSensitivePropertiesTest {
    @Test
    fun sensitivePropertyStoredEncrypted() = runTest {
        val folder = createTestDBFolder("sensitive-rocks-encrypted")
        try {
            val store = RocksDBDataStore.open(
                relativePath = folder,
                keepAllVersions = false,
                dataModelsById = mapOf(1u to SensitiveRocksModel),
                fieldEncryptionProvider = XorFieldEncryptionProvider(),
            )
            try {
                val addResult = store.execute(
                    SensitiveRocksModel.add(
                        SensitiveRocksModel(Bytes(ByteArray(16) { it.toByte() }), "top-secret")
                    )
                )
                val key = assertIs<AddSuccess<SensitiveRocksModel>>(addResult.statuses.single()).key

                val table = store.getColumnFamilies(1u).table
                val keyAndRef = key.bytes + SensitiveRocksModel.secret.ref().toStorageByteArray()
                val rawStored = store.db.get(table, keyAndRef)
                assertNotNull(rawStored)
                val payload = rawStored.copyOfRange(VERSION_BYTE_SIZE, rawStored.size)
                val plain = SensitiveRocksModel.secret.definition.toStorageBytes("top-secret", TypeIndicator.NoTypeIndicator.byte)
                assertFalse(payload.contentEquals(plain))

                val decrypted = store.decryptValueIfNeeded(payload)
                assertContentEquals(plain, decrypted)
            } finally {
                store.close()
            }
        } finally {
            deleteFolder(folder)
        }
    }

    @Test
    fun sensitivePropertyRequiresEncryptionProvider() = runTest {
        val folder = createTestDBFolder("sensitive-rocks-missing-provider")
        try {
            assertFailsWith<RequestException> {
                RocksDBDataStore.open(
                    relativePath = folder,
                    keepAllVersions = false,
                    dataModelsById = mapOf(1u to SensitiveRocksModel),
                )
            }
        } finally {
            deleteFolder(folder)
        }
    }

    @Test
    fun sensitiveUniqueRequiresTokenProvider() = runTest {
        val folder = createTestDBFolder("sensitive-rocks-unique-provider")
        try {
            assertFailsWith<RequestException> {
                RocksDBDataStore.open(
                    relativePath = folder,
                    keepAllVersions = false,
                    dataModelsById = mapOf(2u to SensitiveUniqueRocksModel),
                    fieldEncryptionProvider = XorFieldEncryptionProvider(),
                )
            }
        } finally {
            deleteFolder(folder)
        }
    }

    @Test
    fun sensitiveUniqueUsesDeterministicToken() = runTest {
        val folder = createTestDBFolder("sensitive-rocks-unique-token")
        try {
            val store = RocksDBDataStore.open(
                relativePath = folder,
                keepAllVersions = false,
                dataModelsById = mapOf(2u to SensitiveUniqueRocksModel),
                fieldEncryptionProvider = XorWithTokenFieldEncryptionProvider(),
            )
            try {
                val firstResult: IsAddResponseStatus<SensitiveUniqueRocksModel> = store.execute(
                    SensitiveUniqueRocksModel.add(
                        SensitiveUniqueRocksModel(Bytes(ByteArray(16) { 1 }), "same-secret")
                    )
                ).statuses.single()
                assertIs<AddSuccess<SensitiveUniqueRocksModel>>(firstResult)

                val secondResult: IsAddResponseStatus<SensitiveUniqueRocksModel> = store.execute(
                    SensitiveUniqueRocksModel.add(
                        SensitiveUniqueRocksModel(Bytes(ByteArray(16) { 2 }), "same-secret")
                    )
                ).statuses.single()
                assertIs<ValidationFail<SensitiveUniqueRocksModel>>(secondResult)
            } finally {
                store.close()
            }
        } finally {
            deleteFolder(folder)
        }
    }
}

private class XorFieldEncryptionProvider : FieldEncryptionProvider {
    override suspend fun encrypt(value: ByteArray): ByteArray = xor(value)
    override suspend fun decrypt(value: ByteArray): ByteArray = xor(value)

    private fun xor(value: ByteArray): ByteArray =
        ByteArray(value.size) { i -> (value[i].toInt() xor 0x5A).toByte() }
}

private class XorWithTokenFieldEncryptionProvider :
    FieldEncryptionProvider,
    SensitiveIndexTokenProvider {
    override suspend fun encrypt(value: ByteArray): ByteArray = xor(value)
    override suspend fun decrypt(value: ByteArray): ByteArray = xor(value)

    override suspend fun deriveDeterministicToken(modelId: UInt, reference: ByteArray, value: ByteArray): ByteArray {
        val token = ByteArray(16)
        var i = 0
        for (b in reference) {
            token[i % token.size] = (token[i % token.size].toInt() xor b.toInt() xor 0x21).toByte()
            i++
        }
        for (b in value) {
            token[i % token.size] = (token[i % token.size].toInt() xor b.toInt() xor 0x63).toByte()
            i++
        }
        token[0] = (token[0].toInt() xor modelId.toInt()).toByte()
        return token
    }

    private fun xor(value: ByteArray): ByteArray =
        ByteArray(value.size) { i -> (value[i].toInt() xor 0x5A).toByte() }
}

object SensitiveRocksModel : RootDataModel<SensitiveRocksModel>(
    keyDefinition = { SensitiveRocksModel.id.ref() },
    minimumKeyScanByteRange = 0u,
) {
    val id by fixedBytes(1u, byteSize = 16, final = true)
    val secret by string(2u, sensitive = true)

    operator fun invoke(id: Bytes, secret: String) = create {
        this.id with id
        this.secret with secret
    }
}

object SensitiveUniqueRocksModel : RootDataModel<SensitiveUniqueRocksModel>(
    keyDefinition = { SensitiveUniqueRocksModel.id.ref() },
    minimumKeyScanByteRange = 0u,
) {
    val id by fixedBytes(1u, byteSize = 16, final = true)
    val secret by string(2u, unique = true, sensitive = true)

    operator fun invoke(id: Bytes, secret: String) = create {
        this.id with id
        this.secret with secret
    }
}
