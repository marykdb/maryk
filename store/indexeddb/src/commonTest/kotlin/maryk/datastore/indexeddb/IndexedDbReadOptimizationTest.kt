package maryk.datastore.indexeddb

import kotlinx.coroutines.test.runTest
import maryk.core.exceptions.RequestException
import maryk.core.models.RootDataModel
import maryk.core.models.graph
import maryk.core.processors.datastore.StorageTypeEnum.Value
import maryk.core.properties.definitions.string
import maryk.core.properties.definitions.list
import maryk.core.properties.definitions.StringDefinition
import maryk.core.properties.definitions.reference
import maryk.core.properties.types.Key
import maryk.core.query.changes.Change
import maryk.core.query.changes.change
import maryk.core.query.filters.Equals
import maryk.core.query.pairs.with
import maryk.core.query.requests.add
import maryk.core.query.requests.change
import maryk.core.query.requests.scan
import maryk.core.query.requests.get
import maryk.core.query.responses.statuses.AddSuccess
import maryk.core.query.responses.statuses.ChangeSuccess
import maryk.datastore.shared.encryption.ContextualFieldEncryptionProvider
import maryk.datastore.shared.encryption.FieldEncryptionContext
import maryk.datastore.indexeddb.processors.decodeCurrentSnapshotRecord
import maryk.datastore.indexeddb.processors.decodeSelectedSnapshot
import maryk.datastore.indexeddb.processors.encodeCurrentSnapshot
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertIs
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull

private object ProjectionEncryptionModel : RootDataModel<ProjectionEncryptionModel>() {
    val label by string(index = 1u)
    val secret by string(index = 2u, sensitive = true)
    val tags by list(index = 3u, valueDefinition = StringDefinition(), required = false)
}

private object ReferencedProjectionModel : RootDataModel<ReferencedProjectionModel>() {
    val target by reference(1u, dataModel = { ProjectionEncryptionModel })
}

class IndexedDbReadOptimizationTest {
    @Test
    fun largeRepeatedNativeHeadsOwnTheirContainersAndRetainFilterAndHistoryGuards() = runTest {
        installIndexedDbForTests()
        val provider = ProjectionCountingProvider()
        val store = IndexedDbDataStore.open("large-repeated-native-${Random.nextInt()}",
            mapOf(1u to ProjectionEncryptionModel), keepAllVersions = true, fieldEncryptionProvider = provider)
        try {
            val added = assertIs<AddSuccess<ProjectionEncryptionModel>>(store.execute(ProjectionEncryptionModel.add(
                ProjectionEncryptionModel.create {
                    label with "same"; secret with "private"; tags with List(8_192) { "entry-$it" }
                },
            )).statuses.single())
            provider.decryptions = 0
            val rows = store.execute(ProjectionEncryptionModel.get(added.key, added.key)).values
            assertEquals(1, provider.decryptions)
            @Suppress("UNCHECKED_CAST")
            (rows[0].values[ProjectionEncryptionModel.tags.ref()] as MutableList<String>)[0] = "changed"
            assertEquals("entry-0", rows[1].values[ProjectionEncryptionModel.tags.ref()]!!.first())
            provider.decryptions = 0
            store.execute(ProjectionEncryptionModel.get(added.key, added.key,
                where = Equals(ProjectionEncryptionModel.label.ref() with "same")))
            assertEquals(2, provider.decryptions)
            provider.decryptions = 0
            store.execute(ProjectionEncryptionModel.get(added.key, added.key, toVersion = added.version))
            assertEquals(2, provider.decryptions)
            assertEquals("entry-0", store.execute(ProjectionEncryptionModel.get(added.key))
                .values.single().values[ProjectionEncryptionModel.tags.ref()]!!.first())
        } finally { store.close() }
    }

    @Test
    fun repeatedNativeHeadsDecodeOnceAndOwnTheirMutableResults() = runTest {
        installIndexedDbForTests()
        val provider = ProjectionCountingProvider()
        val store = IndexedDbDataStore.open("repeated-native-${Random.nextInt()}",
            mapOf(1u to ProjectionEncryptionModel), fieldEncryptionProvider = provider)
        try {
            val added = assertIs<AddSuccess<ProjectionEncryptionModel>>(store.execute(ProjectionEncryptionModel.add(
                ProjectionEncryptionModel.create { label with "same"; secret with "private"; tags with listOf("original") },
            )).statuses.single())
            val missing = Key<ProjectionEncryptionModel>(ByteArray(ProjectionEncryptionModel.Meta.keyByteSize) { 0x7f })
            provider.decryptions = 0
            val result = store.execute(ProjectionEncryptionModel.get(added.key, missing, added.key))
            assertEquals(listOf(added.key, added.key), result.values.map { it.key })
            assertEquals(1, provider.decryptions)
            @Suppress("UNCHECKED_CAST")
            val first = result.values[0].values[ProjectionEncryptionModel.tags.ref()] as MutableList<String>
            first += "changed"
            assertEquals(listOf("original"), result.values[1].values[ProjectionEncryptionModel.tags.ref()])
            assertEquals(listOf("original"), store.execute(ProjectionEncryptionModel.get(added.key))
                .values.single().values[ProjectionEncryptionModel.tags.ref()])
        } finally { store.close() }
    }

    @Test
    fun multiReadRejectsSchemaChangesEvenWhenMetadataIsRestored() = runTest {
        installIndexedDbForTests()
        for (legacyHead in listOf(false, true)) {
            val provider = ProjectionCountingProvider()
            val store = IndexedDbDataStore.open("schema-restored-${Random.nextInt()}",
                mapOf(1u to ProjectionEncryptionModel), fieldEncryptionProvider = provider)
            try {
                val added = assertIs<AddSuccess<ProjectionEncryptionModel>>(store.execute(
                    ProjectionEncryptionModel.add(ProjectionEncryptionModel.create {
                        label with "public"
                        secret with "private"
                    })
                ).statuses.single())
                if (legacyHead) {
                    val head = assertNotNull(store.byteStore.get("k:1", added.key.bytes))
                    store.byteStore.put("k:1", added.key.bytes, head.copyOfRange(0, 17))
                }
                val options = assertNotNull(store.byteStore.get("meta", OptionsMetadataKey))
                provider.beforeDecrypt = {
                    store.byteStore.put("meta", OptionsMetadataKey, options + byteArrayOf(99))
                    store.byteStore.put("meta", OptionsMetadataKey, options)
                }
                assertFailsWith<RequestException> {
                    if (legacyHead) store.execute(ProjectionEncryptionModel.get(added.key))
                    else store.execute(ProjectionEncryptionModel.scan(allowTableScan = true))
                }
                provider.beforeDecrypt = {}
                assertEquals("private", store.execute(ProjectionEncryptionModel.get(added.key))
                    .values.single().values[ProjectionEncryptionModel.secret.ref()])
            } finally { store.close() }
        }
    }

    @Test
    fun referencedFiltersRetainTheFinalSchemaGuard() = runTest {
        installIndexedDbForTests()
        val provider = ProjectionCountingProvider()
        val store = IndexedDbDataStore.open("schema-referenced-${Random.nextInt()}",
            mapOf(1u to ProjectionEncryptionModel, 2u to ReferencedProjectionModel), fieldEncryptionProvider = provider)
        try {
            val target = assertIs<AddSuccess<ProjectionEncryptionModel>>(store.execute(
                ProjectionEncryptionModel.add(ProjectionEncryptionModel.create { label with "public"; secret with "private" })
            ).statuses.single())
            val source = assertIs<AddSuccess<ReferencedProjectionModel>>(store.execute(
                ReferencedProjectionModel.add(ReferencedProjectionModel.create { this.target with target.key })
            ).statuses.single())
            val options = assertNotNull(store.byteStore.get("meta", OptionsMetadataKey))
            provider.beforeDecrypt = { store.byteStore.put("meta", OptionsMetadataKey, options + byteArrayOf(99)) }
            assertFailsWith<RequestException> {
                store.execute(ReferencedProjectionModel.get(source.key,
                    where = Equals(ReferencedProjectionModel.ref { this.target { secret } } with "private")))
            }
        } finally { store.close() }
    }

    @Test
    fun coherentNativeHeadsSurviveLaterSchemaChangesButLegacyReadsRemainGuarded() = runTest {
        installIndexedDbForTests()
        for (legacyHead in listOf(false, true)) {
            val provider = ProjectionCountingProvider()
            val store = IndexedDbDataStore.open("schema-head-${Random.nextInt()}",
                mapOf(1u to ProjectionEncryptionModel), fieldEncryptionProvider = provider)
            try {
                val added = assertIs<AddSuccess<ProjectionEncryptionModel>>(store.execute(
                    ProjectionEncryptionModel.add(ProjectionEncryptionModel.create {
                        label with "public"
                        secret with "private"
                    })
                ).statuses.single())
                if (legacyHead) {
                    val head = assertNotNull(store.byteStore.get("k:1", added.key.bytes))
                    store.byteStore.put("k:1", added.key.bytes, head.copyOfRange(0, 17))
                }
                val options = assertNotNull(store.byteStore.get("meta", OptionsMetadataKey))
                provider.beforeDecrypt = {
                    store.byteStore.put("meta", OptionsMetadataKey, options + byteArrayOf(99))
                }
                if (legacyHead) {
                    assertFailsWith<RequestException> { store.execute(ProjectionEncryptionModel.get(added.key)) }
                } else {
                    val value = store.execute(ProjectionEncryptionModel.get(added.key)).values.single()
                    assertEquals("private", value.values[ProjectionEncryptionModel.secret.ref()])
                }
                assertFailsWith<RequestException> { store.execute(ProjectionEncryptionModel.get(added.key)) }
            } finally { store.close() }
        }
    }
    @Test
    fun nativeByteReadsOwnTheirClonesAndQueuedWritesCaptureTheirInput() = runTest {
        installIndexedDbForTests()
        val native = openIndexedDbByteStore("owned-buffers-${Random.nextInt()}", setOf("rows"))
        try {
            val key = byteArrayOf(1)
            val input = byteArrayOf(0, 127, -128, -1)
            native.put("rows", key, input)
            input.fill(42)
            val first = assertNotNull(native.get("rows", key))
            assertContentEquals(byteArrayOf(0, 127, -128, -1), first)
            first.fill(43)
            val batch = native.getBatch("rows", listOf(key, key))
            assertContentEquals(byteArrayOf(0, 127, -128, -1), batch[0])
            assertNotNull(batch[0]).fill(44)
            assertContentEquals(byteArrayOf(0, 127, -128, -1), batch[1])
            val scanned = native.scan("rows").single().second
            scanned.fill(45)
            assertContentEquals(byteArrayOf(0, 127, -128, -1), native.get("rows", key))
        } finally { native.close() }
    }

    @Test
    fun projectedSnapshotsSkipLargePayloadCopiesAndKeepPhysicalRowPresence() = runTest {
        val labelQualifier = ProjectionEncryptionModel.label.ref().toStorageByteArray()
        val secretQualifier = ProjectionEncryptionModel.secret.ref().toStorageByteArray()
        val labelValue = encodeStorageValue(Value, ProjectionEncryptionModel.label.definition, "public")
        val snapshot = encodeCurrentSnapshot(IndexedDbRecordMeta(1uL, 2uL, true), listOf(
            labelQualifier to labelValue,
            secretQualifier to ByteArray(1_048_576),
        ))
        val selection = ProjectionEncryptionModel.graph { listOf(label) }
        val decoded = decodeSelectedSnapshot(ProjectionEncryptionModel, snapshot, selection)
        assertEquals(2, decoded.storedRowCount)
        assertEquals(1, decoded.rows.size)
        assertContentEquals(labelQualifier, decoded.rows.single().first)
        assertContentEquals(labelValue, decoded.rows.single().second)

        val emptySelection = ProjectionEncryptionModel.graph { emptyList() }
        val empty = decodeSelectedSnapshot(ProjectionEncryptionModel, snapshot, emptySelection)
        assertEquals(2, empty.storedRowCount)
        assertEquals(emptyList(), empty.rows)
        // Projection must not make a populated deleted snapshot look like a legacy empty cache.
        val record = assertNotNull(decodeCurrentSnapshotRecord(
            ProjectionEncryptionModel, ByteArray(ProjectionEncryptionModel.Meta.keyByteSize), snapshot, emptySelection,
        ))
        assertEquals(true, record.isDeleted)
        assertEquals(2uL, record.lastVersion)
        // Skipped payloads still have to fit inside the snapshot.
        assertFailsWith<IllegalArgumentException> {
            decodeSelectedSnapshot(ProjectionEncryptionModel, snapshot.copyOf(snapshot.size - 1), selection)
        }
    }

    @Test
    fun getPreservesDuplicatesMissingKeysAndFilteringAcrossNativeBatches() = runTest {
        installIndexedDbForTests()
        val store = IndexedDbDataStore.open(
            "get-pages-${Random.nextInt()}", mapOf(1u to ProjectionEncryptionModel),
            fieldEncryptionProvider = ProjectionCountingProvider(),
        )
        try {
            val added = store.execute(ProjectionEncryptionModel.add(*Array(300) { index ->
                ProjectionEncryptionModel.create {
                    label with "label-$index"
                    secret with "hidden"
                }
            })).statuses.map { assertIs<AddSuccess<ProjectionEncryptionModel>>(it).key }
            val missing = Key<ProjectionEncryptionModel>(ByteArray(ProjectionEncryptionModel.Meta.keyByteSize) { 0xFF.toByte() })
            val keys = added.reversed().toMutableList().apply {
                add(255, missing)
                add(256, added.last())
            }
            val selection = ProjectionEncryptionModel.graph { listOf(label) }
            val response = store.execute(ProjectionEncryptionModel.get(*keys.toTypedArray(), select = selection))
            assertEquals(keys.filter { it != missing }, response.values.map { it.key })
            assertEquals(listOf("label-299", "label-298"), response.values.take(2).map { it.values { label } })
            val filtered = store.execute(ProjectionEncryptionModel.get(
                *keys.toTypedArray(), select = selection,
                where = Equals(ProjectionEncryptionModel.ref { label } with "label-299"),
            ))
            assertEquals(listOf(added.last(), added.last()), filtered.values.map { it.key })
        } finally { store.close() }
    }

    @Test
    fun largeBatchesReadKeysIncrementallyAndPreserveOrderAcrossBoundaries() = runTest {
        installIndexedDbForTests()
        val native = openIndexedDbByteStore("batch-pages-${Random.nextInt()}", setOf("rows"))
        try {
            native.put("rows", byteArrayOf(1), byteArrayOf(10))
            native.put("rows", byteArrayOf(2), byteArrayOf(20))
            var keysRead = 0
            val keys = object : AbstractList<ByteArray>() {
                override val size = 600
                override fun get(index: Int): ByteArray {
                    keysRead++
                    return byteArrayOf((index % 3 + 1).toByte())
                }
            }
            var requested = 0
            val batchSizes = mutableListOf<Int>()
            val batched = object : IndexedDbByteStore by native, IndexedDbBatchReadStore {
                override suspend fun getBatchValues(storeName: String, keys: List<ByteArray>): List<ByteArray?> {
                    requested += keys.size
                    batchSizes += keys.size
                    // Later batches must not be copied before the first native read.
                    assertEquals(requested, keysRead)
                    return (native as IndexedDbBatchReadStore).getBatchValues(storeName, keys)
                }
            }
            assertEquals(emptyList(), batched.getBatch("rows", emptyList()))
            assertEquals(emptyList(), batchSizes)
            val values = batched.getBatch("rows", keys)
            assertEquals(listOf(256, 256, 88), batchSizes)
            assertEquals(600, values.size)
            for (index in values.indices) {
                when (index % 3) {
                    0 -> assertContentEquals(byteArrayOf(10), values[index])
                    1 -> assertContentEquals(byteArrayOf(20), values[index])
                    2 -> assertNull(values[index])
                }
            }
            // A custom adapter without native batching retains the same ordered fallback.
            val fallback = object : IndexedDbByteStore by native {}
            val fallbackValues = fallback.getBatch("rows", keys)
            for (index in values.indices) assertContentEquals(values[index], fallbackValues[index])
        } finally { native.close() }
    }

    @Test
    fun batchedReadsPreserveMissingKeysDuplicatesAndStagedWrites() = runTest {
        installIndexedDbForTests()
        val store = openIndexedDbByteStore("batch-order-${Random.nextInt()}", setOf("rows"))
        try {
            store.writeBatch(listOf(
                IndexedDbWriteOperation.Put("rows", byteArrayOf(1), byteArrayOf(10)),
                IndexedDbWriteOperation.Put("rows", byteArrayOf(2), byteArrayOf(20)),
            ))
            val keys = listOf(byteArrayOf(2), byteArrayOf(3), byteArrayOf(1), byteArrayOf(2))
            val values = store.getBatch("rows", keys)
            assertEquals(4, values.size)
            assertContentEquals(byteArrayOf(20), values[0])
            assertNull(values[1])
            assertContentEquals(byteArrayOf(10), values[2])
            assertContentEquals(byteArrayOf(20), values[3])

            val staged = StagedIndexedDbByteStore(store)
            staged.put("rows", byteArrayOf(3), byteArrayOf(30))
            staged.delete("rows", byteArrayOf(2))
            val overlay = staged.getBatch("rows", keys)
            assertNull(overlay[0])
            assertContentEquals(byteArrayOf(30), overlay[1])
            assertContentEquals(byteArrayOf(10), overlay[2])
            assertNull(overlay[3])
            assertContentEquals(byteArrayOf(20), store.get("rows", byteArrayOf(2)))
            assertNull(store.get("rows", byteArrayOf(3)))
        } finally { store.close() }
    }

    @Test
    fun projectedCurrentAndHistoricReadsDecryptOnlySelectedValuesAndStillFilter() = runTest {
        installIndexedDbForTests()
        val provider = ProjectionCountingProvider()
        val store = IndexedDbDataStore.open(
            "projection-encryption-${Random.nextInt()}", mapOf(1u to ProjectionEncryptionModel),
            keepAllVersions = true, fieldEncryptionProvider = provider,
        )
        try {
            val add = assertIs<AddSuccess<ProjectionEncryptionModel>>(store.execute(
                ProjectionEncryptionModel.add(ProjectionEncryptionModel.create {
                    label with "first"
                    secret with "hidden"
                })
            ).statuses.single())
            val publicSelection = ProjectionEncryptionModel.graph { listOf(label) }
            provider.decryptions = 0
            val public = store.execute(ProjectionEncryptionModel.get(add.key, select = publicSelection)).values.single()
            assertEquals("first", public.values[ProjectionEncryptionModel.label.ref()])
            assertNull(public.values[ProjectionEncryptionModel.secret.ref()])
            assertEquals(0, provider.decryptions)

            provider.decryptions = 0
            val scanned = store.execute(ProjectionEncryptionModel.scan(select = publicSelection,
                allowTableScan = true)).values.single()
            assertEquals("first", scanned.values[ProjectionEncryptionModel.label.ref()])
            assertEquals(0, provider.decryptions)

            provider.decryptions = 0
            val selected = store.execute(ProjectionEncryptionModel.get(add.key,
                select = ProjectionEncryptionModel.graph { listOf(secret) })).values.single()
            assertEquals("hidden", selected.values[ProjectionEncryptionModel.secret.ref()])
            assertEquals(1, provider.decryptions)

            provider.decryptions = 0
            val filtered = store.execute(ProjectionEncryptionModel.get(add.key, select = publicSelection,
                where = Equals(ProjectionEncryptionModel.ref { secret } with "hidden"))).values.single()
            assertEquals("first", filtered.values[ProjectionEncryptionModel.label.ref()])
            assertNull(filtered.values[ProjectionEncryptionModel.secret.ref()])
            assertEquals(1, provider.decryptions)
            assertIs<ChangeSuccess<ProjectionEncryptionModel>>(store.execute(ProjectionEncryptionModel.change(
                add.key.change(Change(ProjectionEncryptionModel.ref { label } with "second"))
            )).statuses.single())

            provider.decryptions = 0
            val historic = store.execute(ProjectionEncryptionModel.get(add.key, toVersion = add.version,
                select = publicSelection)).values.single()
            assertEquals("first", historic.values[ProjectionEncryptionModel.label.ref()])
            assertEquals(0, provider.decryptions)
        } finally { store.close() }
    }
}

private class ProjectionCountingProvider : ContextualFieldEncryptionProvider {
    var decryptions = 0
    var beforeDecrypt: suspend () -> Unit = {}
    override suspend fun encrypt(value: ByteArray, offset: Int, length: Int) = value.copyOfRange(offset, offset + length)
    override suspend fun decrypt(value: ByteArray, offset: Int, length: Int): ByteArray {
        beforeDecrypt()
        decryptions++
        return value.copyOfRange(offset, offset + length)
    }
    override suspend fun encrypt(context: FieldEncryptionContext, value: ByteArray, offset: Int, length: Int) = encrypt(value, offset, length)
    override suspend fun decrypt(context: FieldEncryptionContext, value: ByteArray, offset: Int, length: Int) = decrypt(value, offset, length)
}
