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
import maryk.core.query.requests.change
import maryk.core.query.requests.delete
import maryk.core.query.requests.get
import maryk.core.query.requests.scan
import maryk.core.query.changes.Change
import maryk.core.query.changes.change
import maryk.core.query.filters.Equals
import maryk.core.query.pairs.with
import maryk.core.query.responses.statuses.AddSuccess
import maryk.core.query.responses.statuses.ChangeSuccess
import maryk.core.query.responses.statuses.ServerFail
import maryk.core.query.responses.statuses.DeleteSuccess
import maryk.core.query.responses.statuses.IsAddResponseStatus
import maryk.core.query.responses.statuses.ValidationFail
import maryk.createTestDBFolder
import maryk.datastore.shared.encryption.ContextualFieldEncryptionProvider
import maryk.datastore.shared.encryption.FieldEncryptionProvider
import maryk.datastore.shared.encryption.FieldEncryptionContext
import maryk.datastore.shared.encryption.FieldEncryptionEnvelope
import maryk.datastore.shared.encryption.AesGcmHmacSha256EncryptionProvider
import maryk.datastore.shared.encryption.SensitiveIndexTokenProvider
import maryk.datastore.shared.TypeIndicator
import maryk.datastore.rocksdb.processors.helpers.VERSION_BYTE_SIZE
import maryk.deleteFolder
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
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

                val decrypted = store.decryptValueIfNeeded(1u, key.bytes, SensitiveRocksModel.secret.ref().toStorageByteArray(), payload)
                assertContentEquals(plain, decrypted)
                assertEquals(
                    "top-secret",
                    store.execute(SensitiveRocksModel.get(key)).values.single().values { secret },
                )
            } finally {
                store.close()
            }
        } finally {
            deleteFolder(folder)
        }
    }

    @Test
    fun nonSensitiveMkePrefixValuesRoundTripWithAndWithoutProvider() = runTest {
        listOf<XorFieldEncryptionProvider?>(null, XorFieldEncryptionProvider()).forEachIndexed { providerIndex, provider ->
            val folder = createTestDBFolder("non-sensitive-mke-prefix-$providerIndex")
            try {
                val store = RocksDBDataStore.open(
                    relativePath = folder,
                    keepAllVersions = false,
                    dataModelsById = mapOf(11u to MkePrefixRocksModel),
                    fieldEncryptionProvider = provider,
                )
                try {
                    FieldEncryptionEnvelope.entries.forEachIndexed { envelopeIndex, envelope ->
                        val text = envelope.magic.decodeToString() + " public text"
                        val bytes = Bytes(envelope.magic + byteArrayOf(1, 2, 3, 4))
                        val key = assertIs<AddSuccess<MkePrefixRocksModel>>(
                            store.execute(
                                MkePrefixRocksModel.add(
                                    MkePrefixRocksModel(
                                        Bytes(ByteArray(16) { envelopeIndex.toByte() }),
                                        text,
                                        bytes,
                                    )
                                )
                            ).statuses.single()
                        ).key

                        val values = store.execute(MkePrefixRocksModel.get(key)).values.single()
                        assertEquals(text, values.values { publicText })
                        assertContentEquals(bytes.bytes, requireNotNull(values.values { publicBytes }).bytes)
                        assertContentEquals(
                            text.encodeToByteArray(),
                            store.decryptValueIfNeeded(11u, key.bytes, MkePrefixRocksModel.publicText.ref().toStorageByteArray(), text.encodeToByteArray()),
                        )
                        assertContentEquals(
                            bytes.bytes,
                            store.decryptValueIfNeeded(11u, key.bytes, MkePrefixRocksModel.publicBytes.ref().toStorageByteArray(), bytes.bytes),
                        )
                    }
                } finally {
                    store.close()
                }
            } finally {
                deleteFolder(folder)
            }
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
    fun contextualCiphertextCannotBeTransplantedAndLegacyCiphertextStillReads() = runTest {
        val folder = createTestDBFolder("sensitive-rocks-contextual-envelope")
        val provider = AesGcmHmacSha256EncryptionProvider(
            encryptionKey = ByteArray(32) { it.toByte() },
            tokenKey = ByteArray(32) { (it + 32).toByte() },
        )
        try {
            val store = RocksDBDataStore.open(
                relativePath = folder,
                keepAllVersions = false,
                dataModelsById = mapOf(1u to SensitiveRocksModel),
                fieldEncryptionProvider = provider,
            )
            try {
                val firstKey = assertIs<AddSuccess<SensitiveRocksModel>>(
                    store.execute(SensitiveRocksModel.add(SensitiveRocksModel(Bytes(ByteArray(16) { 1 }), "secret"))).statuses.single()
                ).key
                val secondKey = assertIs<AddSuccess<SensitiveRocksModel>>(
                    store.execute(SensitiveRocksModel.add(SensitiveRocksModel(Bytes(ByteArray(16) { 2 }), "other"))).statuses.single()
                ).key
                val reference = SensitiveRocksModel.secret.ref().toStorageByteArray()
                val storedValue = store.db.get(store.getColumnFamilies(1u).table, firstKey.bytes + reference)!!
                val payload = storedValue.copyOfRange(VERSION_BYTE_SIZE, storedValue.size)

                assertFailsWith<Throwable> {
                    store.decryptValueIfNeeded(1u, secondKey.bytes, reference, payload)
                }

                Transaction(store).use { transaction ->
                    transaction.put(store.getColumnFamilies(1u).table, secondKey.bytes + reference, storedValue)
                    transaction.commit()
                }
                assertFailsWith<Throwable> {
                    store.execute(SensitiveRocksModel.get(secondKey))
                }

                val plain = SensitiveRocksModel.secret.definition.toStorageBytes("legacy", TypeIndicator.NoTypeIndicator.byte)
                val legacyPayload = FieldEncryptionEnvelope.Legacy.magic + provider.encrypt(plain)
                assertContentEquals(
                    plain,
                    store.decryptValueIfNeeded(1u, firstKey.bytes, reference, legacyPayload),
                )
            } finally {
                store.close()
            }
        } finally {
            deleteFolder(folder)
        }
    }

    @Test
    fun contextualEncryptionReadsHistoricSensitiveValues() = runTest {
        val folder = createTestDBFolder("sensitive-rocks-contextual-history")
        val provider = contextualProvider()
        try {
            val store = RocksDBDataStore.open(
                relativePath = folder,
                keepAllVersions = true,
                dataModelsById = mapOf(1u to SensitiveRocksModel),
                fieldEncryptionProvider = provider,
            )
            try {
                val add = assertIs<AddSuccess<SensitiveRocksModel>>(
                    store.execute(SensitiveRocksModel.add(SensitiveRocksModel(Bytes(ByteArray(16) { 1 }), "before"))).statuses.single()
                )
                val beforeChange = store.captureSnapshotVersion()
                assertIs<ChangeSuccess<SensitiveRocksModel>>(
                    store.execute(
                        SensitiveRocksModel.change(
                            add.key.change(Change(SensitiveRocksModel { secret::ref } with "after"))
                        )
                    ).statuses.single()
                )
                assertEquals("after", store.execute(SensitiveRocksModel.get(add.key)).values.single().values { secret })
                assertEquals(
                    "before",
                    store.execute(SensitiveRocksModel.get(add.key, toVersion = beforeChange)).values.single().values { secret },
                )
                val historicScan = store.execute(
                    SensitiveRocksModel.scan(
                        where = Equals(SensitiveRocksModel { secret::ref } with "before"),
                        toVersion = beforeChange,
                        allowTableScan = true,
                    )
                )
                assertEquals(1, historicScan.values.size)
                assertEquals("before", historicScan.values.single().values { secret })
            } finally {
                store.close()
            }
        } finally {
            deleteFolder(folder)
        }
    }

    @Test
    fun legacyOnlyProviderReadsMke1AndCannotWriteNewSensitiveValues() = runTest {
        val folder = createTestDBFolder("sensitive-rocks-legacy-provider")
        val contextualProvider = contextualProvider()
        var key: Key<SensitiveRocksModel>? = null
        try {
            val initialStore = RocksDBDataStore.open(
                relativePath = folder,
                keepAllVersions = false,
                dataModelsById = mapOf(1u to SensitiveRocksModel),
                fieldEncryptionProvider = contextualProvider,
            )
            try {
                val store = initialStore
                key = assertIs<AddSuccess<SensitiveRocksModel>>(
                    store.execute(SensitiveRocksModel.add(SensitiveRocksModel(Bytes(ByteArray(16) { 1 }), "legacy"))).statuses.single()
                ).key
                val reference = SensitiveRocksModel.secret.ref().toStorageByteArray()
                val stored = store.db.get(store.getColumnFamilies(1u).table, key.bytes + reference)!!
                val plain = SensitiveRocksModel.secret.definition.toStorageBytes("legacy", TypeIndicator.NoTypeIndicator.byte)
                store.db.put(
                    store.getColumnFamilies(1u).table,
                    key.bytes + reference,
                    stored.copyOfRange(0, VERSION_BYTE_SIZE) + FieldEncryptionEnvelope.Legacy.magic + contextualProvider.encrypt(plain),
                )
            } finally {
                initialStore.close()
            }

            val legacyStore = RocksDBDataStore.open(
                relativePath = folder,
                keepAllVersions = false,
                dataModelsById = mapOf(1u to SensitiveRocksModel),
                fieldEncryptionProvider = LegacyOnlyFieldEncryptionProvider(contextualProvider),
            )
            try {
                assertEquals("legacy", legacyStore.execute(SensitiveRocksModel.get(key)).values.single().values { secret })
                assertIs<ServerFail<SensitiveRocksModel>>(
                    legacyStore.execute(SensitiveRocksModel.add(SensitiveRocksModel(Bytes(ByteArray(16) { 2 }), "new"))).statuses.single()
                )
            } finally {
                legacyStore.close()
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
    fun deletingSensitiveUniqueValueDerivesTokenFromDecryptedValueWithoutCopying() = runTest {
        val folder = createTestDBFolder("sensitive-rocks-unique-delete-range")
        val encryptionProvider = XorWithTokenFieldEncryptionProvider()
        try {
            val store = RocksDBDataStore.open(
                relativePath = folder,
                keepAllVersions = false,
                dataModelsById = mapOf(2u to SensitiveUniqueRocksModel),
                fieldEncryptionProvider = encryptionProvider,
            )
            try {
                val key = assertIs<AddSuccess<SensitiveUniqueRocksModel>>(
                    store.execute(
                        SensitiveUniqueRocksModel.add(
                            SensitiveUniqueRocksModel(Bytes(ByteArray(16) { 1 }), "secret")
                        )
                    ).statuses.single()
                ).key
                encryptionProvider.clearTracking()

                assertIs<DeleteSuccess<SensitiveUniqueRocksModel>>(
                    store.execute(SensitiveUniqueRocksModel.delete(key)).statuses.single()
                )

                assertTrue(encryptionProvider.decryptedValues.isNotEmpty())
                assertTrue(encryptionProvider.tokenInputs.any { tokenInput ->
                    encryptionProvider.decryptedValues.any { decryptedValue -> decryptedValue === tokenInput }
                })
            } finally {
                store.close()
            }
        } finally {
            deleteFolder(folder)
        }
    }

    @Test
    fun sensitiveUniqueCandidateMappingPreservesProvidedRange() = runTest {
        val folder = createTestDBFolder("sensitive-rocks-unique-candidate-range")
        val encryptionProvider = XorWithTokenFieldEncryptionProvider()
        try {
            val store = RocksDBDataStore.open(
                relativePath = folder,
                keepAllVersions = false,
                dataModelsById = mapOf(2u to SensitiveUniqueRocksModel),
                fieldEncryptionProvider = encryptionProvider,
            )
            try {
                val reference = SensitiveUniqueRocksModel.secret.ref().toStorageByteArray()
                val value = SensitiveUniqueRocksModel.secret.definition.toStorageBytes(
                    "secret",
                    TypeIndicator.NoTypeIndicator.byte,
                )
                val paddedValue = byteArrayOf(1, 2) + value + byteArrayOf(3)
                val directToken = store.mapUniqueValueByteCandidates(2u, reference, value).single()
                encryptionProvider.clearTracking()

                val rangedToken = store.mapUniqueValueByteCandidates(
                    2u,
                    reference,
                    paddedValue,
                    offset = 2,
                    length = value.size,
                ).single()

                assertContentEquals(directToken, rangedToken)
                assertEquals(listOf(2), encryptionProvider.tokenOffsets)
                assertEquals(listOf(value.size), encryptionProvider.tokenLengths)
                assertTrue(encryptionProvider.tokenInputs.single() === paddedValue)
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

private fun contextualProvider() = AesGcmHmacSha256EncryptionProvider(
    encryptionKey = ByteArray(32) { it.toByte() },
    tokenKey = ByteArray(32) { (it + 32).toByte() },
)

private class LegacyOnlyFieldEncryptionProvider(
    private val delegate: FieldEncryptionProvider,
) : FieldEncryptionProvider {
    override suspend fun encrypt(value: ByteArray, offset: Int, length: Int): ByteArray =
        delegate.encrypt(value, offset, length)

    override suspend fun decrypt(value: ByteArray, offset: Int, length: Int): ByteArray =
        delegate.decrypt(value, offset, length)
}

private class XorFieldEncryptionProvider : ContextualFieldEncryptionProvider {
    override suspend fun encrypt(value: ByteArray, offset: Int, length: Int): ByteArray = xor(value, offset, length)
    override suspend fun decrypt(value: ByteArray, offset: Int, length: Int): ByteArray = xor(value, offset, length)

    override suspend fun encrypt(context: FieldEncryptionContext, value: ByteArray, offset: Int, length: Int): ByteArray = encrypt(value, offset, length)
    override suspend fun decrypt(context: FieldEncryptionContext, value: ByteArray, offset: Int, length: Int): ByteArray = decrypt(value, offset, length)

    private fun xor(value: ByteArray, offset: Int, length: Int): ByteArray =
        ByteArray(length) { i -> (value[offset + i].toInt() xor 0x5A).toByte() }
}

private class BlockingEncryptFieldEncryptionProvider : ContextualFieldEncryptionProvider {
    val encryptStarted = CompletableDeferred<Unit>()
    val releaseEncrypt = CompletableDeferred<Unit>()

    override suspend fun encrypt(value: ByteArray, offset: Int, length: Int): ByteArray {
        encryptStarted.complete(Unit)
        releaseEncrypt.await()
        return xor(value, offset, length)
    }

    override suspend fun decrypt(value: ByteArray, offset: Int, length: Int): ByteArray =
        xor(value, offset, length)

    override suspend fun encrypt(context: FieldEncryptionContext, value: ByteArray, offset: Int, length: Int): ByteArray = encrypt(value, offset, length)
    override suspend fun decrypt(context: FieldEncryptionContext, value: ByteArray, offset: Int, length: Int): ByteArray = decrypt(value, offset, length)

    private fun xor(value: ByteArray, offset: Int, length: Int): ByteArray =
        ByteArray(length) { index -> (value[offset + index].toInt() xor 0x5A).toByte() }
}

private class XorWithTokenFieldEncryptionProvider :
    ContextualFieldEncryptionProvider,
    SensitiveIndexTokenProvider {
    override suspend fun encrypt(value: ByteArray, offset: Int, length: Int): ByteArray = xor(value, offset, length)
    val decryptedValues = mutableListOf<ByteArray>()
    val tokenInputs = mutableListOf<ByteArray>()
    val tokenOffsets = mutableListOf<Int>()
    val tokenLengths = mutableListOf<Int>()

    override suspend fun decrypt(value: ByteArray, offset: Int, length: Int): ByteArray =
        xor(value, offset, length).also(decryptedValues::add)
    override suspend fun encrypt(context: FieldEncryptionContext, value: ByteArray, offset: Int, length: Int): ByteArray = encrypt(value, offset, length)
    override suspend fun decrypt(context: FieldEncryptionContext, value: ByteArray, offset: Int, length: Int): ByteArray = decrypt(value, offset, length)

    override suspend fun deriveDeterministicToken(modelId: UInt, reference: ByteArray, value: ByteArray, offset: Int, length: Int): ByteArray {
        tokenInputs += value
        tokenOffsets += offset
        tokenLengths += length
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

    fun clearTracking() {
        decryptedValues.clear()
        tokenInputs.clear()
        tokenOffsets.clear()
        tokenLengths.clear()
    }

    private fun xor(value: ByteArray, offset: Int, length: Int): ByteArray =
        ByteArray(length) { i -> (value[offset + i].toInt() xor 0x5A).toByte() }
}

private class RotatingTokenFieldEncryptionProvider(
    private val activeToken: Int,
    private val previousTokens: List<Int> = emptyList(),
) : ContextualFieldEncryptionProvider, SensitiveIndexTokenProvider {
    override suspend fun encrypt(value: ByteArray, offset: Int, length: Int): ByteArray = xor(value, offset, length)
    override suspend fun decrypt(value: ByteArray, offset: Int, length: Int): ByteArray = xor(value, offset, length)
    override suspend fun encrypt(context: FieldEncryptionContext, value: ByteArray, offset: Int, length: Int): ByteArray = encrypt(value, offset, length)
    override suspend fun decrypt(context: FieldEncryptionContext, value: ByteArray, offset: Int, length: Int): ByteArray = decrypt(value, offset, length)

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

private object MkePrefixRocksModel : RootDataModel<MkePrefixRocksModel>(
    keyDefinition = { MkePrefixRocksModel.id.ref() },
    minimumKeyScanByteRange = 0u,
) {
    val id by fixedBytes(1u, byteSize = 16, final = true)
    val publicText by string(2u)
    val publicBytes by fixedBytes(3u, byteSize = 8)

    operator fun invoke(id: Bytes, publicText: String, publicBytes: Bytes) = create {
        this.id with id
        this.publicText with publicText
        this.publicBytes with publicBytes
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
