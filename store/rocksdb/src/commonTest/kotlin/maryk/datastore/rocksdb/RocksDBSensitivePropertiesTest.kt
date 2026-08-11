package maryk.datastore.rocksdb

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.yield
import maryk.core.exceptions.RequestException
import maryk.core.models.RootDataModel
import maryk.core.properties.definitions.fixedBytes
import maryk.core.properties.definitions.string
import maryk.core.properties.types.Bytes
import maryk.core.properties.types.Key
import maryk.core.query.requests.add
import maryk.core.query.requests.delete
import maryk.core.query.responses.statuses.AddSuccess
import maryk.core.query.responses.statuses.DeleteSuccess
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
import kotlin.test.assertTrue

class RocksDBSensitivePropertiesTest {
    @Test
    fun snapshotCaptureWaitsForMutationCompletion() = runTest {
        val folder = createTestDBFolder("mutation-version-publication")
        val encryptionProvider = BlockingEncryptFieldEncryptionProvider()
        try {
            val store = RocksDBDataStore.open(
                relativePath = folder,
                keepAllVersions = true,
                dataModelsById = mapOf(1u to SensitiveRocksModel),
                fieldEncryptionProvider = encryptionProvider,
            )
            try {
                val beforeMutation = store.captureSnapshotVersion()
                val mutation = async {
                    store.execute(
                        SensitiveRocksModel.add(
                            SensitiveRocksModel(Bytes(ByteArray(16) { it.toByte() }), "secret")
                        )
                    )
                }

                encryptionProvider.encryptStarted.await()
                val snapshot = async { store.captureSnapshotVersion() }
                yield()
                assertFalse(snapshot.isCompleted)
                encryptionProvider.releaseEncrypt.complete(Unit)
                mutation.await()
                assertTrue(snapshot.await() > beforeMutation)
            } finally {
                encryptionProvider.releaseEncrypt.complete(Unit)
                store.close()
            }
        } finally {
            deleteFolder(folder)
        }
    }

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

    @Test
    fun softDeleteRemovesRetainedSensitiveUniqueRotationToken() = runTest {
        val folder = createTestDBFolder("sensitive-rocks-unique-rotation")
        try {
            val oldStore = RocksDBDataStore.open(
                relativePath = folder,
                keepAllVersions = false,
                dataModelsById = mapOf(2u to SensitiveUniqueRocksModel),
                fieldEncryptionProvider = RotatingTokenFieldEncryptionProvider(1),
            )
            val key = try {
                assertIs<AddSuccess<SensitiveUniqueRocksModel>>(
                    oldStore.execute(
                        SensitiveUniqueRocksModel.add(SensitiveUniqueRocksModel(Bytes(ByteArray(16) { 1 }), "same-secret"))
                    ).statuses.single()
                ).key
            } finally {
                oldStore.close()
            }

            val rotatedStore = RocksDBDataStore.open(
                relativePath = folder,
                keepAllVersions = false,
                dataModelsById = mapOf(2u to SensitiveUniqueRocksModel),
                fieldEncryptionProvider = RotatingTokenFieldEncryptionProvider(2, listOf(1)),
            )
            try {
                assertIs<DeleteSuccess<SensitiveUniqueRocksModel>>(
                    rotatedStore.execute(SensitiveUniqueRocksModel.delete(key)).statuses.single()
                )
                assertIs<AddSuccess<SensitiveUniqueRocksModel>>(
                    rotatedStore.execute(
                        SensitiveUniqueRocksModel.add(SensitiveUniqueRocksModel(Bytes(ByteArray(16) { 2 }), "same-secret"))
                    ).statuses.single()
                )
            } finally {
                rotatedStore.close()
            }
        } finally {
            deleteFolder(folder)
        }
    }

    @Test
    fun hardDeleteRemovesRetainedTokenHistoryAfterSoftDeleteWithoutErasingReusedTokenHistory() = runTest {
        val folder = createTestDBFolder("sensitive-rocks-unique-hard-delete-rotation")
        try {
            val oldStore = RocksDBDataStore.open(
                relativePath = folder,
                keepAllVersions = true,
                dataModelsById = mapOf(2u to SensitiveUniqueRocksModel),
                fieldEncryptionProvider = RotatingTokenFieldEncryptionProvider(1),
            )
            val otherKey: Key<SensitiveUniqueRocksModel>
            val targetKey: Key<SensitiveUniqueRocksModel>
            try {
                otherKey = assertIs<AddSuccess<SensitiveUniqueRocksModel>>(
                    oldStore.execute(
                        SensitiveUniqueRocksModel.add(SensitiveUniqueRocksModel(Bytes(ByteArray(16) { 1 }), "same-secret"))
                    ).statuses.single()
                ).key
                assertIs<DeleteSuccess<SensitiveUniqueRocksModel>>(
                    oldStore.execute(SensitiveUniqueRocksModel.delete(otherKey)).statuses.single()
                )
                targetKey = assertIs<AddSuccess<SensitiveUniqueRocksModel>>(
                    oldStore.execute(
                        SensitiveUniqueRocksModel.add(SensitiveUniqueRocksModel(Bytes(ByteArray(16) { 2 }), "same-secret"))
                    ).statuses.single()
                ).key
                assertIs<DeleteSuccess<SensitiveUniqueRocksModel>>(
                    oldStore.execute(SensitiveUniqueRocksModel.delete(targetKey)).statuses.single()
                )
            } finally {
                oldStore.close()
            }

            val rotatedStore = RocksDBDataStore.open(
                relativePath = folder,
                keepAllVersions = true,
                dataModelsById = mapOf(2u to SensitiveUniqueRocksModel),
                fieldEncryptionProvider = RotatingTokenFieldEncryptionProvider(2, listOf(1)),
            )
            try {
                assertIs<DeleteSuccess<SensitiveUniqueRocksModel>>(
                    rotatedStore.execute(SensitiveUniqueRocksModel.delete(targetKey, hardDelete = true)).statuses.single()
                )
                val historicOwners = rotatedStore.historicUniqueOwners(2u)
                assertTrue(historicOwners.any { it.contentEquals(otherKey.bytes) })
                assertFalse(historicOwners.any { it.contentEquals(targetKey.bytes) })
            } finally {
                rotatedStore.close()
            }
        } finally {
            deleteFolder(folder)
        }
    }
}

private fun RocksDBDataStore.historicUniqueOwners(modelId: UInt): List<ByteArray> {
    val columnFamilies = getColumnFamilies(modelId) as HistoricTableColumnFamilies
    return DBAccessor(this).use { accessor ->
        accessor.getIterator(defaultReadOptions, columnFamilies.historic.unique).use { iterator ->
            buildList {
                iterator.seekToFirst()
                while (iterator.isValid()) {
                    add(iterator.value())
                    iterator.next()
                }
            }
        }
    }
}

private class XorFieldEncryptionProvider : FieldEncryptionProvider {
    override suspend fun encrypt(value: ByteArray, offset: Int, length: Int): ByteArray = xor(value, offset, length)
    override suspend fun decrypt(value: ByteArray, offset: Int, length: Int): ByteArray = xor(value, offset, length)

    private fun xor(value: ByteArray, offset: Int, length: Int): ByteArray =
        ByteArray(length) { i -> (value[offset + i].toInt() xor 0x5A).toByte() }
}

private class BlockingEncryptFieldEncryptionProvider : FieldEncryptionProvider {
    val encryptStarted = CompletableDeferred<Unit>()
    val releaseEncrypt = CompletableDeferred<Unit>()

    override suspend fun encrypt(value: ByteArray, offset: Int, length: Int): ByteArray {
        encryptStarted.complete(Unit)
        releaseEncrypt.await()
        return xor(value, offset, length)
    }

    override suspend fun decrypt(value: ByteArray, offset: Int, length: Int): ByteArray =
        xor(value, offset, length)

    private fun xor(value: ByteArray, offset: Int, length: Int): ByteArray =
        ByteArray(length) { index -> (value[offset + index].toInt() xor 0x5A).toByte() }
}

private class XorWithTokenFieldEncryptionProvider :
    FieldEncryptionProvider,
    SensitiveIndexTokenProvider {
    override suspend fun encrypt(value: ByteArray, offset: Int, length: Int): ByteArray = xor(value, offset, length)
    override suspend fun decrypt(value: ByteArray, offset: Int, length: Int): ByteArray = xor(value, offset, length)

    override suspend fun deriveDeterministicToken(modelId: UInt, reference: ByteArray, value: ByteArray, offset: Int, length: Int): ByteArray {
        val token = ByteArray(16)
        var i = 0
        for (b in reference) {
            token[i % token.size] = (token[i % token.size].toInt() xor b.toInt() xor 0x21).toByte()
            i++
        }
        for (index in offset until offset + length) {
            val b = value[index]
            token[i % token.size] = (token[i % token.size].toInt() xor b.toInt() xor 0x63).toByte()
            i++
        }
        token[0] = (token[0].toInt() xor modelId.toInt()).toByte()
        return token
    }

    private fun xor(value: ByteArray, offset: Int, length: Int): ByteArray =
        ByteArray(length) { i -> (value[offset + i].toInt() xor 0x5A).toByte() }
}

private class RotatingTokenFieldEncryptionProvider(
    private val activeToken: Int,
    private val previousTokens: List<Int> = emptyList(),
) : FieldEncryptionProvider, SensitiveIndexTokenProvider {
    override suspend fun encrypt(value: ByteArray, offset: Int, length: Int): ByteArray = xor(value, offset, length)
    override suspend fun decrypt(value: ByteArray, offset: Int, length: Int): ByteArray = xor(value, offset, length)

    override suspend fun deriveDeterministicToken(
        modelId: UInt,
        reference: ByteArray,
        value: ByteArray,
        offset: Int,
        length: Int,
    ): ByteArray = ByteArray(16) { activeToken.toByte() }

    override suspend fun deriveDeterministicTokenCandidates(
        modelId: UInt,
        reference: ByteArray,
        value: ByteArray,
        offset: Int,
        length: Int,
    ): List<ByteArray> = (listOf(activeToken) + previousTokens)
        .distinct()
        .map { token -> ByteArray(16) { token.toByte() } }

    private fun xor(value: ByteArray, offset: Int, length: Int): ByteArray =
        ByteArray(length) { index -> (value[offset + index].toInt() xor 0x5A).toByte() }
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
