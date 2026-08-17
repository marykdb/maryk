package maryk.datastore.foundationdb

import maryk.core.exceptions.RequestException
import maryk.core.models.RootDataModel
import maryk.core.properties.definitions.fixedBytes
import maryk.core.properties.definitions.reference
import maryk.core.properties.definitions.string
import maryk.core.properties.types.Bytes
import maryk.core.properties.types.Key
import maryk.core.query.changes.Change
import maryk.core.query.changes.change
import maryk.core.query.filters.Equals
import maryk.core.query.pairs.with
import maryk.core.query.requests.add
import maryk.core.query.requests.change
import maryk.core.query.requests.delete
import maryk.core.query.requests.get
import maryk.core.query.requests.scan
import maryk.core.query.responses.statuses.IsAddResponseStatus
import maryk.core.query.responses.statuses.AddSuccess
import maryk.core.query.responses.statuses.ChangeSuccess
import maryk.core.query.responses.statuses.DeleteSuccess
import maryk.core.query.responses.statuses.ValidationFail
import maryk.datastore.shared.encryption.AesGcmHmacSha256EncryptionProvider
import maryk.datastore.shared.encryption.EncryptedFieldRecord
import maryk.datastore.shared.encryption.FieldEncryptionContext
import maryk.datastore.shared.encryption.ContextualFieldEncryptionProvider
import maryk.datastore.shared.encryption.FieldEncryptionEnvelope
import maryk.datastore.shared.encryption.KeyringFieldEncryptionProvider
import maryk.datastore.shared.encryption.ReEncryptionBatch
import maryk.datastore.shared.encryption.ReEncryptionState
import maryk.datastore.shared.encryption.SensitiveIndexTokenProvider
import maryk.datastore.shared.encryption.runReEncryptionBatch
import maryk.datastore.foundationdb.processors.helpers.VERSION_BYTE_SIZE
import maryk.datastore.foundationdb.processors.helpers.awaitResult
import maryk.datastore.foundationdb.processors.distinctHistoricUniqueReferences
import maryk.datastore.foundationdb.processors.helpers.nextBlocking
import maryk.datastore.foundationdb.processors.helpers.packKey
import maryk.datastore.shared.TypeIndicator
import maryk.foundationdb.Range as FDBRange
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.uuid.Uuid

class FieldEncryptionTest {
    @Test
    fun clusterUpdateLogRejectsModelsWithSensitiveProperties() {
        runBoundedIntegrationTest {
            var openedStore: FoundationDBDataStore? = null
            try {
                assertFailsWith<RequestException> {
                    openedStore = FoundationDBDataStore.open(
                        fdbClusterFilePath = "./fdb.cluster",
                        directoryPath = listOf("maryk", "test", "sensitive-cluster-log", Uuid.random().toString()),
                        dataModelsById = mapOf(901u to SensitiveRecord),
                        keepAllVersions = false,
                        clusterUpdateLogConfiguration = FoundationDBClusterUpdateLogConfiguration(
                            enableClusterUpdateLog = true,
                            clusterUpdateLogConsumerId = "sensitive-cluster-log-test",
                        ),
                        fieldEncryptionProvider = XorFieldEncryptionProvider(),
                    )
                }
            } finally {
                openedStore?.close()
            }
        }
    }

    @Test
    fun reEncryptionBatchRotatesPersistedContextualField() {
        runBoundedIntegrationTest {
            val directoryPath = listOf("maryk", "test", "persisted-field-rotation", Uuid.random().toString())
            val oldProvider = AesGcmHmacSha256EncryptionProvider(
                encryptionKey = ByteArray(32) { 1 },
                tokenKey = ByteArray(32) { 2 },
            )
            val currentProvider = AesGcmHmacSha256EncryptionProvider(
                encryptionKey = ByteArray(32) { 3 },
                tokenKey = ByteArray(32) { 4 },
            )
            val newKeyring = KeyringFieldEncryptionProvider(
                activeKeyId = "current",
                providers = mapOf("old" to oldProvider, "current" to currentProvider),
                legacyProvider = oldProvider,
            )
            var store = FoundationDBDataStore.open(
                fdbClusterFilePath = "./fdb.cluster",
                directoryPath = directoryPath,
                dataModelsById = mapOf(901u to SensitiveRecord),
                keepAllVersions = false,
                fieldEncryptionProvider = oldProvider,
            )
            try {
                val key = assertIs<AddSuccess<SensitiveRecord>>(
                    store.execute(
                        SensitiveRecord.add(
                            SensitiveRecord(Bytes(ByteArray(16) { it.toByte() }), "hello", "rotate-me")
                        )
                    ).statuses.single()
                ).key
                store.close()
                store = FoundationDBDataStore.open(
                    fdbClusterFilePath = "./fdb.cluster",
                    directoryPath = directoryPath,
                    dataModelsById = mapOf(901u to SensitiveRecord),
                    keepAllVersions = false,
                    fieldEncryptionProvider = newKeyring,
                )

                val sensitiveReference = SensitiveRecord.secret.ref().toStorageByteArray()
                val storedKey = packKey(store.getTableDirs(901u).tablePrefix, key.bytes, sensitiveReference)
                val storedValue = assertNotNull(store.runTransaction { tr ->
                    tr.get(storedKey).awaitResult()
                })
                val persistedPayload = storedValue.copyOfRange(VERSION_BYTE_SIZE, storedValue.size)

                runReEncryptionBatch(
                    provider = newKeyring,
                    state = ReEncryptionState(targetKeyId = "current"),
                    read = {
                        ReEncryptionBatch(
                            records = listOf(
                                EncryptedFieldRecord(
                                    id = storedKey,
                                    modelId = 901u,
                                    recordKey = key.bytes,
                                    reference = sensitiveReference,
                                    payload = persistedPayload,
                                )
                            ),
                            nextCursor = null,
                        )
                    },
                    write = { id, payload ->
                        store.runTransaction { tr ->
                            tr.set(id, storedValue.copyOfRange(0, VERSION_BYTE_SIZE) + payload)
                        }
                    },
                    persistState = {},
                )

                assertEquals("rotate-me", store.execute(SensitiveRecord.get(key)).values.single().values { secret })
                val rotatedValue = assertNotNull(store.runTransaction { tr -> tr.get(storedKey).awaitResult() })
                val keyringOffset = VERSION_BYTE_SIZE + FieldEncryptionEnvelope.Contextual.magic.size
                assertEquals(
                    "current",
                    newKeyring.keyId(rotatedValue, keyringOffset, rotatedValue.size - keyringOffset),
                )
            } finally {
                store.close()
            }
        }
    }

    @Test
    fun sensitivePropertyStoredEncrypted() {
        runBoundedIntegrationTest {
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

                val decrypted = store.decryptValueIfNeeded(901u, key.bytes, sensitiveRef, payload)
                assertContentEquals(plain, decrypted)
            } finally {
                store.close()
            }
        }
    }

    @Test
    fun nonSensitiveMkePrefixValuesRoundTripWithAndWithoutProvider() {
        runBoundedIntegrationTest {
            listOf<XorFieldEncryptionProvider?>(null, XorFieldEncryptionProvider()).forEachIndexed { providerIndex, provider ->
                val store = FoundationDBDataStore.open(
                    fdbClusterFilePath = "./fdb.cluster",
                    directoryPath = listOf("maryk", "test", "non-sensitive-mke-prefix", providerIndex.toString(), Uuid.random().toString()),
                    dataModelsById = mapOf(905u to MkePrefixRecord),
                    keepAllVersions = false,
                    fieldEncryptionProvider = provider,
                )
                try {
                    FieldEncryptionEnvelope.entries.forEachIndexed { envelopeIndex, envelope ->
                        val text = envelope.magic.decodeToString() + " public text"
                        val bytes = Bytes(envelope.magic + byteArrayOf(1, 2, 3, 4))
                        val key = assertIs<AddSuccess<MkePrefixRecord>>(
                            store.execute(
                                MkePrefixRecord.add(
                                    MkePrefixRecord(
                                        Bytes(ByteArray(16) { envelopeIndex.toByte() }),
                                        text,
                                        bytes,
                                    )
                                )
                            ).statuses.single()
                        ).key

                        val values = store.execute(MkePrefixRecord.get(key)).values.single()
                        assertEquals(text, values.values { publicText })
                        assertContentEquals(bytes.bytes, requireNotNull(values.values { publicBytes }).bytes)
                        assertContentEquals(
                            text.encodeToByteArray(),
                            store.decryptValueIfNeeded(905u, key.bytes, MkePrefixRecord.publicText.ref().toStorageByteArray(), text.encodeToByteArray()),
                        )
                        assertContentEquals(
                            bytes.bytes,
                            store.decryptValueIfNeeded(905u, key.bytes, MkePrefixRecord.publicBytes.ref().toStorageByteArray(), bytes.bytes),
                        )
                    }
                } finally {
                    store.close()
                }
            }
        }
    }

    @Test
    fun contextualEnvelopeRejectsTransplantAndReadsLegacyEnvelope() {
        runBoundedIntegrationTest {
            val provider = AesGcmHmacSha256EncryptionProvider(
                encryptionKey = ByteArray(32) { 1 },
                tokenKey = ByteArray(32) { 2 },
            )
            val store = FoundationDBDataStore.open(
                fdbClusterFilePath = "./fdb.cluster",
                directoryPath = listOf("maryk", "test", "field-encryption-context", Uuid.random().toString()),
                dataModelsById = mapOf(901u to SensitiveRecord),
                keepAllVersions = false,
                fieldEncryptionProvider = provider,
            )
            try {
                val firstKey = assertIs<AddSuccess<SensitiveRecord>>(
                    store.execute(SensitiveRecord.add(SensitiveRecord(Bytes(ByteArray(16) { 1 }), "first", "secret"))).statuses.single()
                ).key
                val secondKey = assertIs<AddSuccess<SensitiveRecord>>(
                    store.execute(SensitiveRecord.add(SensitiveRecord(Bytes(ByteArray(16) { 2 }), "second", "secret"))).statuses.single()
                ).key
                val tableDirs = store.getTableDirs(901u)
                val sensitiveRef = SensitiveRecord.secret.ref().toStorageByteArray()
                val rawStored = assertNotNull(store.runTransaction { tr ->
                    tr.get(packKey(tableDirs.tablePrefix, firstKey.bytes, sensitiveRef)).awaitResult()
                })
                val payload = rawStored.copyOfRange(VERSION_BYTE_SIZE, rawStored.size)
                assertFailsWith<Exception> {
                    store.decryptValueIfNeeded(901u, secondKey.bytes, sensitiveRef, payload)
                }
                store.runTransaction { tr ->
                    tr.set(
                        packKey(tableDirs.tablePrefix, secondKey.bytes, sensitiveRef),
                        rawStored,
                    )
                }
                assertFailsWith<Exception> {
                    store.execute(SensitiveRecord.get(secondKey))
                }

                val legacyPlain = SensitiveRecord.secret.definition.toStorageBytes("legacy-secret", TypeIndicator.NoTypeIndicator.byte)
                val legacyPayload = provider.encrypt(legacyPlain)
                val legacyEnvelope = byteArrayOf(0x4D, 0x4B, 0x45, 0x31) + legacyPayload
                store.runTransaction { tr ->
                    tr.set(
                        packKey(tableDirs.tablePrefix, firstKey.bytes, sensitiveRef),
                        rawStored.copyOfRange(0, VERSION_BYTE_SIZE) + legacyEnvelope,
                    )
                }
                assertContentEquals(
                    legacyPlain,
                    store.decryptValueIfNeeded(901u, firstKey.bytes, sensitiveRef, legacyEnvelope),
                )
                assertEquals(
                    "legacy-secret",
                    store.execute(SensitiveRecord.get(firstKey)).values.single().values { secret },
                )
            } finally {
                store.close()
            }
        }
    }

    @Test
    fun contextualEncryptionReadsHistoricSensitiveValues() {
        runBoundedIntegrationTest {
            val store = FoundationDBDataStore.open(
                fdbClusterFilePath = "./fdb.cluster",
                directoryPath = listOf("maryk", "test", "field-encryption-history", Uuid.random().toString()),
                dataModelsById = mapOf(901u to SensitiveRecord),
                keepAllVersions = true,
                fieldEncryptionProvider = AesGcmHmacSha256EncryptionProvider(
                    encryptionKey = ByteArray(32) { 3 },
                    tokenKey = ByteArray(32) { 4 },
                ),
            )
            try {
                val add = assertIs<AddSuccess<SensitiveRecord>>(
                    store.execute(SensitiveRecord.add(SensitiveRecord(Bytes(ByteArray(16) { 1 }), "record", "before"))).statuses.single()
                )
                assertIs<ChangeSuccess<SensitiveRecord>>(
                    store.execute(
                        SensitiveRecord.change(
                            add.key.change(Change(SensitiveRecord { secret::ref } with "after"))
                        )
                    ).statuses.single()
                )
                assertEquals("after", store.execute(SensitiveRecord.get(add.key)).values.single().values { secret })
                assertEquals(
                    "before",
                    store.execute(SensitiveRecord.get(add.key, toVersion = add.version)).values.single().values { secret },
                )
            } finally {
                store.close()
            }
        }
    }

    @Test
    fun contextualEncryptionFiltersCurrentSensitiveValuesDuringTableScan() {
        runBoundedIntegrationTest {
            val store = FoundationDBDataStore.open(
                fdbClusterFilePath = "./fdb.cluster",
                directoryPath = listOf("maryk", "test", "field-encryption-scan", Uuid.random().toString()),
                dataModelsById = mapOf(901u to SensitiveRecord),
                keepAllVersions = false,
                fieldEncryptionProvider = AesGcmHmacSha256EncryptionProvider(ByteArray(32) { 5 }, ByteArray(32) { 6 }),
            )
            try {
                store.execute(SensitiveRecord.add(SensitiveRecord(Bytes(ByteArray(16) { 1 }), "match", "wanted")))
                store.execute(SensitiveRecord.add(SensitiveRecord(Bytes(ByteArray(16) { 2 }), "skip", "other")))

                val response = store.execute(
                    SensitiveRecord.scan(
                        where = Equals(SensitiveRecord { secret::ref } with "wanted"),
                        allowTableScan = true,
                    )
                )
                assertEquals(1, response.values.size)
                assertEquals("wanted", response.values.single().values { secret })
            } finally {
                store.close()
            }
        }
    }

    @Test
    fun referencedFilterUsesTargetModelContextForSensitiveValue() {
        runBoundedIntegrationTest {
            val store = FoundationDBDataStore.open(
                fdbClusterFilePath = "./fdb.cluster",
                directoryPath = listOf("maryk", "test", "field-encryption-referenced-filter", Uuid.random().toString()),
                dataModelsById = mapOf(911u to SensitiveReferenceOwner, 912u to SensitiveReferenceTarget),
                keepAllVersions = false,
                fieldEncryptionProvider = AesGcmHmacSha256EncryptionProvider(ByteArray(32) { 7 }, ByteArray(32) { 8 }),
            )
            try {
                val target = assertIs<AddSuccess<SensitiveReferenceTarget>>(
                    store.execute(SensitiveReferenceTarget.add(SensitiveReferenceTarget(Bytes(ByteArray(16) { 1 }), "wanted"))).statuses.single()
                ).key
                val other = assertIs<AddSuccess<SensitiveReferenceTarget>>(
                    store.execute(SensitiveReferenceTarget.add(SensitiveReferenceTarget(Bytes(ByteArray(16) { 2 }), "other"))).statuses.single()
                ).key
                store.execute(SensitiveReferenceOwner.add(SensitiveReferenceOwner(Bytes(ByteArray(16) { 3 }), target)))
                store.execute(SensitiveReferenceOwner.add(SensitiveReferenceOwner(Bytes(ByteArray(16) { 4 }), other)))

                val response = store.execute(
                    SensitiveReferenceOwner.scan(
                        where = Equals(SensitiveReferenceOwner { target { secret::ref } } with "wanted"),
                        allowTableScan = true,
                    )
                )
                assertEquals(1, response.values.size)
            } finally {
                store.close()
            }
        }
    }

    @Test
    fun sensitivePropertyRequiresEncryptionProvider() {
        runBoundedIntegrationTest {
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
        runBoundedIntegrationTest {
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
        runBoundedIntegrationTest {
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
        runBoundedIntegrationTest {
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

    @Test
    fun deletingSensitiveUniqueValuePassesDecryptedRangeToTokenProvider() {
        runBoundedIntegrationTest {
            val encryptionProvider = XorWithTokenFieldEncryptionProvider()
            val store = FoundationDBDataStore.open(
                fdbClusterFilePath = "./fdb.cluster",
                directoryPath = listOf("maryk", "test", "field-encryption-unique-delete-range", Uuid.random().toString()),
                dataModelsById = mapOf(904u to SensitiveUniqueRecord),
                keepAllVersions = false,
                fieldEncryptionProvider = encryptionProvider,
            )
            try {
                val key = assertIs<AddSuccess<SensitiveUniqueRecord>>(
                    store.execute(
                        SensitiveUniqueRecord.add(SensitiveUniqueRecord(Bytes(ByteArray(16) { 1 }), "secret"))
                    ).statuses.single()
                ).key
                encryptionProvider.clearTracking()

                assertIs<DeleteSuccess<SensitiveUniqueRecord>>(
                    store.execute(SensitiveUniqueRecord.delete(key)).statuses.single()
                )

                assertTrue(encryptionProvider.decryptedValues.isNotEmpty())
                assertTrue(encryptionProvider.tokenInputs.any { tokenInput ->
                    encryptionProvider.decryptedValues.any { decryptedValue -> decryptedValue === tokenInput }
                })
                assertEquals(listOf(0), encryptionProvider.tokenOffsets)
                assertEquals(
                    listOf(SensitiveUniqueRecord.secret.definition.toStorageBytes("secret", TypeIndicator.NoTypeIndicator.byte).size),
                    encryptionProvider.tokenLengths,
                )
            } finally {
                store.close()
            }
        }
    }

    @Test
    fun softDeleteRemovesRetainedSensitiveUniqueRotationToken() {
        runBoundedIntegrationTest {
            val directoryPath = listOf("maryk", "test", "field-encryption-unique-rotation", Uuid.random().toString())
            val oldStore = FoundationDBDataStore.open(
                fdbClusterFilePath = "./fdb.cluster",
                directoryPath = directoryPath,
                dataModelsById = mapOf(904u to SensitiveUniqueRecord),
                keepAllVersions = false,
                fieldEncryptionProvider = RotatingTokenFieldEncryptionProvider(1),
            )
            val key = try {
                assertIs<AddSuccess<SensitiveUniqueRecord>>(
                    oldStore.execute(
                        SensitiveUniqueRecord.add(SensitiveUniqueRecord(Bytes(ByteArray(16) { 1 }), "same-secret"))
                    ).statuses.single()
                ).key
            } finally {
                oldStore.close()
            }

            val rotatedStore = FoundationDBDataStore.open(
                fdbClusterFilePath = "./fdb.cluster",
                directoryPath = directoryPath,
                dataModelsById = mapOf(904u to SensitiveUniqueRecord),
                keepAllVersions = false,
                fieldEncryptionProvider = RotatingTokenFieldEncryptionProvider(2, listOf(1)),
            )
            try {
                assertIs<DeleteSuccess<SensitiveUniqueRecord>>(
                    rotatedStore.execute(SensitiveUniqueRecord.delete(key)).statuses.single()
                )
                assertIs<AddSuccess<SensitiveUniqueRecord>>(
                    rotatedStore.execute(
                        SensitiveUniqueRecord.add(SensitiveUniqueRecord(Bytes(ByteArray(16) { 2 }), "same-secret"))
                    ).statuses.single()
                )
            } finally {
                rotatedStore.close()
            }
        }
    }

    @Test
    fun hardDeleteRemovesRetainedTokenHistoryAfterSoftDeleteWithoutErasingReusedTokenHistory() {
        runBoundedIntegrationTest {
            val directoryPath = listOf("maryk", "test", "field-encryption-unique-hard-delete-rotation", Uuid.random().toString())
            val oldStore = FoundationDBDataStore.open(
                fdbClusterFilePath = "./fdb.cluster",
                directoryPath = directoryPath,
                dataModelsById = mapOf(904u to SensitiveUniqueRecord),
                keepAllVersions = true,
                fieldEncryptionProvider = RotatingTokenFieldEncryptionProvider(1),
            )
            val otherKey: Key<SensitiveUniqueRecord>
            val targetKey: Key<SensitiveUniqueRecord>
            try {
                otherKey = assertIs<AddSuccess<SensitiveUniqueRecord>>(
                    oldStore.execute(
                        SensitiveUniqueRecord.add(SensitiveUniqueRecord(Bytes(ByteArray(16) { 1 }), "same-secret"))
                    ).statuses.single()
                ).key
                assertIs<DeleteSuccess<SensitiveUniqueRecord>>(
                    oldStore.execute(SensitiveUniqueRecord.delete(otherKey)).statuses.single()
                )
                targetKey = assertIs<AddSuccess<SensitiveUniqueRecord>>(
                    oldStore.execute(
                        SensitiveUniqueRecord.add(SensitiveUniqueRecord(Bytes(ByteArray(16) { 2 }), "same-secret"))
                    ).statuses.single()
                ).key
                assertIs<DeleteSuccess<SensitiveUniqueRecord>>(
                    oldStore.execute(SensitiveUniqueRecord.delete(targetKey)).statuses.single()
                )
            } finally {
                oldStore.close()
            }

            val rotatedStore = FoundationDBDataStore.open(
                fdbClusterFilePath = "./fdb.cluster",
                directoryPath = directoryPath,
                dataModelsById = mapOf(904u to SensitiveUniqueRecord),
                keepAllVersions = true,
                fieldEncryptionProvider = RotatingTokenFieldEncryptionProvider(2, listOf(1)),
            )
            try {
                assertIs<DeleteSuccess<SensitiveUniqueRecord>>(
                    rotatedStore.execute(SensitiveUniqueRecord.delete(targetKey, hardDelete = true)).statuses.single()
                )
                val historicOwners = rotatedStore.historicUniqueOwners(904u)
                assertTrue(historicOwners.any { it.contentEquals(otherKey.bytes) })
                assertFalse(historicOwners.any { it.contentEquals(targetKey.bytes) })
            } finally {
                rotatedStore.close()
            }
        }
    }

    @Test
    fun hardDeleteRemovesHistoricTokensForChangedSensitiveUniqueValueWithoutErasingOtherOwnerHistory() {
        runBoundedIntegrationTest {
            val store = FoundationDBDataStore.open(
                fdbClusterFilePath = "./fdb.cluster",
                directoryPath = listOf("maryk", "test", "field-encryption-unique-hard-delete-changed", Uuid.random().toString()),
                dataModelsById = mapOf(904u to SensitiveUniqueRecord),
                keepAllVersions = true,
                fieldEncryptionProvider = XorWithTokenFieldEncryptionProvider(),
            )
            try {
                val otherKey = assertIs<AddSuccess<SensitiveUniqueRecord>>(
                    store.execute(
                        SensitiveUniqueRecord.add(SensitiveUniqueRecord(Bytes(ByteArray(16) { 1 }), "old-secret"))
                    ).statuses.single()
                ).key
                assertIs<DeleteSuccess<SensitiveUniqueRecord>>(
                    store.execute(SensitiveUniqueRecord.delete(otherKey)).statuses.single()
                )
                val targetKey = assertIs<AddSuccess<SensitiveUniqueRecord>>(
                    store.execute(
                        SensitiveUniqueRecord.add(SensitiveUniqueRecord(Bytes(ByteArray(16) { 2 }), "old-secret"))
                    ).statuses.single()
                ).key
                assertIs<ChangeSuccess<SensitiveUniqueRecord>>(
                    store.execute(
                        SensitiveUniqueRecord.change(
                            targetKey.change(Change(SensitiveUniqueRecord { secret::ref } with "new-secret"))
                        )
                    ).statuses.single()
                )

                assertIs<DeleteSuccess<SensitiveUniqueRecord>>(
                    store.execute(SensitiveUniqueRecord.delete(targetKey, hardDelete = true)).statuses.single()
                )
                val historicOwners = store.historicUniqueOwners(904u)
                assertTrue(historicOwners.any { it.contentEquals(otherKey.bytes) })
                assertFalse(historicOwners.any { it.contentEquals(targetKey.bytes) })
            } finally {
                store.close()
            }
        }
    }

    @Test
    fun hardDeleteDeduplicatesRepeatedHistoricValuesAndDuplicateRetainedTokens() {
        runBoundedIntegrationTest {
            val store = FoundationDBDataStore.open(
                fdbClusterFilePath = "./fdb.cluster",
                directoryPath = listOf("maryk", "test", "field-encryption-unique-hard-delete-deduplicate", Uuid.random().toString()),
                dataModelsById = mapOf(904u to SensitiveUniqueRecord),
                keepAllVersions = true,
                fieldEncryptionProvider = DuplicateRetainedTokenFieldEncryptionProvider(),
            )
            try {
                val otherKey = assertIs<AddSuccess<SensitiveUniqueRecord>>(
                    store.execute(
                        SensitiveUniqueRecord.add(SensitiveUniqueRecord(Bytes(ByteArray(16) { 1 }), "other"))
                    ).statuses.single()
                ).key
                assertIs<DeleteSuccess<SensitiveUniqueRecord>>(
                    store.execute(SensitiveUniqueRecord.delete(otherKey)).statuses.single()
                )
                val targetKey = assertIs<AddSuccess<SensitiveUniqueRecord>>(
                    store.execute(
                        SensitiveUniqueRecord.add(SensitiveUniqueRecord(Bytes(ByteArray(16) { 2 }), "first"))
                    ).statuses.single()
                ).key
                for (value in listOf("second", "first", "second", "first")) {
                    assertIs<ChangeSuccess<SensitiveUniqueRecord>>(
                        store.execute(
                            SensitiveUniqueRecord.change(
                                targetKey.change(Change(SensitiveUniqueRecord { secret::ref } with value))
                            )
                        ).statuses.single()
                    )
                }

                assertIs<DeleteSuccess<SensitiveUniqueRecord>>(
                    store.execute(SensitiveUniqueRecord.delete(targetKey, hardDelete = true)).statuses.single()
                )
                val historicOwners = store.historicUniqueOwners(904u)
                assertTrue(historicOwners.any { it.contentEquals(otherKey.bytes) })
                assertFalse(historicOwners.any { it.contentEquals(targetKey.bytes) })
            } finally {
                store.close()
            }
        }
    }

    @Test
    fun distinctHistoricUniqueReferencesKeepsEachReferenceTokenCandidateOnce() {
        val first = byteArrayOf(3, 1)
        val second = byteArrayOf(3, 2)
        val distinct = distinctHistoricUniqueReferences(listOf(first, second, first.copyOf(), second.copyOf(), first.copyOf()))

        assertEquals(2, distinct.size)
        assertContentEquals(first, distinct[0])
        assertContentEquals(second, distinct[1])
    }
}

private suspend fun FoundationDBDataStore.historicUniqueOwners(modelId: UInt): List<ByteArray> {
    val tableDirs = getTableDirs(modelId) as HistoricTableDirectories
    return runTransaction { tr ->
        buildList {
            val iterator = tr.getRange(FDBRange.startsWith(tableDirs.historicUniquePrefix)).iterator()
            while (iterator.hasNext()) {
                add(iterator.nextBlocking().value)
            }
        }
    }
}

private class XorFieldEncryptionProvider : ContextualFieldEncryptionProvider {
    override suspend fun encrypt(value: ByteArray, offset: Int, length: Int): ByteArray = xor(value, offset, length)
    override suspend fun decrypt(value: ByteArray, offset: Int, length: Int): ByteArray = xor(value, offset, length)

    override suspend fun encrypt(context: FieldEncryptionContext, value: ByteArray, offset: Int, length: Int): ByteArray = xor(value, offset, length)
    override suspend fun decrypt(context: FieldEncryptionContext, value: ByteArray, offset: Int, length: Int): ByteArray = xor(value, offset, length)

    private fun xor(value: ByteArray, offset: Int, length: Int): ByteArray =
        ByteArray(length) { i -> (value[offset + i].toInt() xor 0x5A).toByte() }
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

    override suspend fun encrypt(context: FieldEncryptionContext, value: ByteArray, offset: Int, length: Int): ByteArray = xor(value, offset, length)
    override suspend fun decrypt(context: FieldEncryptionContext, value: ByteArray, offset: Int, length: Int): ByteArray =
        xor(value, offset, length).also(decryptedValues::add)

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

    override suspend fun encrypt(context: FieldEncryptionContext, value: ByteArray, offset: Int, length: Int): ByteArray = xor(value, offset, length)
    override suspend fun decrypt(context: FieldEncryptionContext, value: ByteArray, offset: Int, length: Int): ByteArray = xor(value, offset, length)

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

private class DuplicateRetainedTokenFieldEncryptionProvider :
    ContextualFieldEncryptionProvider,
    SensitiveIndexTokenProvider {
    override suspend fun encrypt(value: ByteArray, offset: Int, length: Int): ByteArray = xor(value, offset, length)
    override suspend fun decrypt(value: ByteArray, offset: Int, length: Int): ByteArray = xor(value, offset, length)

    override suspend fun encrypt(context: FieldEncryptionContext, value: ByteArray, offset: Int, length: Int): ByteArray = xor(value, offset, length)
    override suspend fun decrypt(context: FieldEncryptionContext, value: ByteArray, offset: Int, length: Int): ByteArray = xor(value, offset, length)

    override suspend fun deriveDeterministicToken(
        modelId: UInt,
        reference: ByteArray,
        value: ByteArray,
        offset: Int,
        length: Int,
    ): ByteArray = ByteArray(16) { 1 }

    override suspend fun deriveDeterministicTokenCandidates(
        modelId: UInt,
        reference: ByteArray,
        value: ByteArray,
        offset: Int,
        length: Int,
    ): List<ByteArray> = listOf(
        ByteArray(16) { 1 },
        ByteArray(16) { 2 },
        ByteArray(16) { 1 },
        ByteArray(16) { 2 },
    )

    private fun xor(value: ByteArray, offset: Int, length: Int): ByteArray =
        ByteArray(length) { index -> (value[offset + index].toInt() xor 0x5A).toByte() }
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

private object MkePrefixRecord : RootDataModel<MkePrefixRecord>(
    keyDefinition = { MkePrefixRecord.id.ref() },
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

private object SensitiveReferenceTarget : RootDataModel<SensitiveReferenceTarget>(
    keyDefinition = { SensitiveReferenceTarget.id.ref() },
    minimumKeyScanByteRange = 0u,
) {
    val id by fixedBytes(1u, byteSize = 16, final = true)
    val secret by string(2u, sensitive = true)

    operator fun invoke(id: Bytes, secret: String) = create {
        this.id with id
        this.secret with secret
    }
}

private object SensitiveReferenceOwner : RootDataModel<SensitiveReferenceOwner>(
    keyDefinition = { SensitiveReferenceOwner.id.ref() },
    minimumKeyScanByteRange = 0u,
) {
    val id by fixedBytes(1u, byteSize = 16, final = true)
    val target by reference(index = 2u, dataModel = { SensitiveReferenceTarget })

    operator fun invoke(id: Bytes, target: Key<SensitiveReferenceTarget>) = create {
        this.id with id
        this.target with target
    }
}
