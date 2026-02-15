@file:OptIn(kotlin.uuid.ExperimentalUuidApi::class)

package maryk.datastore.foundationdb

import kotlinx.coroutines.runBlocking
import maryk.core.exceptions.RequestException
import maryk.core.models.RootDataModel
import maryk.core.properties.definitions.fixedBytes
import maryk.core.properties.definitions.string
import maryk.core.properties.types.Bytes
import maryk.core.query.requests.add
import maryk.core.query.responses.statuses.IsAddResponseStatus
import maryk.core.query.responses.statuses.AddSuccess
import maryk.core.query.responses.statuses.ValidationFail
import maryk.datastore.shared.encryption.FieldEncryptionProvider
import maryk.datastore.shared.encryption.SensitiveIndexTokenProvider
import maryk.datastore.foundationdb.processors.helpers.VERSION_BYTE_SIZE
import maryk.datastore.foundationdb.processors.helpers.awaitResult
import maryk.datastore.foundationdb.processors.helpers.packKey
import maryk.datastore.shared.TypeIndicator
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.uuid.Uuid

class FieldEncryptionTest {
    @Test
    fun sensitivePropertyStoredEncrypted() {
        runBlocking {
            val store = FoundationDBDataStore.open(
                fdbClusterFilePath = "./fdb.cluster",
                directoryPath = listOf("maryk", "test", "field-encryption", Uuid.random().toString()),
                dataModelsById = mapOf(901u to SensitiveRecord),
                keepAllVersions = false,
                fieldEncryptionProvider = XorFieldEncryptionProvider(),
            )

            try {
                val addResult = store.execute(
                    SensitiveRecord.add(
                        SensitiveRecord(Bytes(ByteArray(16) { it.toByte() }), "hello", "top-secret")
                    )
                )
                val key = assertIs<AddSuccess<SensitiveRecord>>(addResult.statuses.single()).key

                val tableDirs = store.getTableDirs(901u)
                val sensitiveRef = SensitiveRecord.secret.ref().toStorageByteArray()
                val rawStored = store.runTransaction { tr ->
                    tr.get(packKey(tableDirs.tablePrefix, key.bytes, sensitiveRef)).awaitResult()
                }
                assertNotNull(rawStored)

                val payload = rawStored.copyOfRange(VERSION_BYTE_SIZE, rawStored.size)
                val plain = SensitiveRecord.secret.definition.toStorageBytes("top-secret", TypeIndicator.NoTypeIndicator.byte)
                assertFalse(payload.contentEquals(plain))

                val decrypted = store.decryptValueIfNeeded(payload)
                assertContentEquals(plain, decrypted)
            } finally {
                store.close()
            }
        }
    }

    @Test
    fun sensitivePropertyRequiresEncryptionProvider() {
        runBlocking {
            assertFailsWith<RequestException> {
                FoundationDBDataStore.open(
                    fdbClusterFilePath = "./fdb.cluster",
                    directoryPath = listOf("maryk", "test", "field-encryption-missing", Uuid.random().toString()),
                    dataModelsById = mapOf(901u to SensitiveRecord),
                    keepAllVersions = false,
                )
            }
        }
    }

    @Test
    fun sensitivePropertyCannotBeIndexed() {
        runBlocking {
            assertFailsWith<RequestException> {
                FoundationDBDataStore.open(
                    fdbClusterFilePath = "./fdb.cluster",
                    directoryPath = listOf("maryk", "test", "field-encryption-indexed", Uuid.random().toString()),
                    dataModelsById = mapOf(902u to SensitiveIndexedRecord),
                    keepAllVersions = false,
                    fieldEncryptionProvider = XorFieldEncryptionProvider(),
                )
            }
        }
    }

    @Test
    fun sensitiveUniqueRequiresTokenProvider() {
        runBlocking {
            assertFailsWith<RequestException> {
                FoundationDBDataStore.open(
                    fdbClusterFilePath = "./fdb.cluster",
                    directoryPath = listOf("maryk", "test", "field-encryption-unique-provider", Uuid.random().toString()),
                    dataModelsById = mapOf(904u to SensitiveUniqueRecord),
                    keepAllVersions = false,
                    fieldEncryptionProvider = XorFieldEncryptionProvider(),
                )
            }
        }
    }

    @Test
    fun sensitiveUniqueUsesDeterministicToken() {
        runBlocking {
            val store = FoundationDBDataStore.open(
                fdbClusterFilePath = "./fdb.cluster",
                directoryPath = listOf("maryk", "test", "field-encryption-unique", Uuid.random().toString()),
                dataModelsById = mapOf(904u to SensitiveUniqueRecord),
                keepAllVersions = false,
                fieldEncryptionProvider = XorWithTokenFieldEncryptionProvider(),
            )

            try {
                val firstResult: IsAddResponseStatus<SensitiveUniqueRecord> = store.execute(
                    SensitiveUniqueRecord.add(
                        SensitiveUniqueRecord(Bytes(ByteArray(16) { 1 }), "same-secret")
                    )
                ).statuses.single()
                assertIs<AddSuccess<SensitiveUniqueRecord>>(firstResult)

                val secondResult: IsAddResponseStatus<SensitiveUniqueRecord> = store.execute(
                    SensitiveUniqueRecord.add(
                        SensitiveUniqueRecord(Bytes(ByteArray(16) { 2 }), "same-secret")
                    )
                ).statuses.single()
                assertIs<ValidationFail<SensitiveUniqueRecord>>(secondResult)
            } finally {
                store.close()
            }
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

object SensitiveRecord : RootDataModel<SensitiveRecord>(
    keyDefinition = { SensitiveRecord.id.ref() },
    minimumKeyScanByteRange = 0u,
) {
    val id by fixedBytes(1u, byteSize = 16, final = true)
    val publicText by string(2u)
    val secret by string(3u, sensitive = true)

    operator fun invoke(id: Bytes, publicText: String, secret: String) = create {
        this.id with id
        this.publicText with publicText
        this.secret with secret
    }
}

object SensitiveIndexedRecord : RootDataModel<SensitiveIndexedRecord>(
    keyDefinition = { SensitiveIndexedRecord.id.ref() },
    indexes = { listOf(SensitiveIndexedRecord.secret.ref()) },
    minimumKeyScanByteRange = 0u,
) {
    val id by fixedBytes(1u, byteSize = 16, final = true)
    val secret by string(2u, sensitive = true)
}

object SensitiveUniqueRecord : RootDataModel<SensitiveUniqueRecord>(
    keyDefinition = { SensitiveUniqueRecord.id.ref() },
    minimumKeyScanByteRange = 0u,
) {
    val id by fixedBytes(1u, byteSize = 16, final = true)
    val secret by string(2u, unique = true, sensitive = true)

    operator fun invoke(id: Bytes, secret: String) = create {
        this.id with id
        this.secret with secret
    }
}
