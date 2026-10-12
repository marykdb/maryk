package maryk.datastore.indexeddb

import kotlinx.coroutines.test.runTest
import maryk.core.models.RootDataModel
import maryk.core.models.graph
import maryk.core.properties.definitions.string
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
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertIs

private object ProjectionEncryptionModel : RootDataModel<ProjectionEncryptionModel>() {
    val label by string(index = 1u)
    val secret by string(index = 2u, sensitive = true)
}

class IndexedDbReadOptimizationTest {
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
    override suspend fun encrypt(value: ByteArray, offset: Int, length: Int) = value.copyOfRange(offset, offset + length)
    override suspend fun decrypt(value: ByteArray, offset: Int, length: Int) =
        value.copyOfRange(offset, offset + length).also { decryptions++ }
    override suspend fun encrypt(context: FieldEncryptionContext, value: ByteArray, offset: Int, length: Int) = encrypt(value, offset, length)
    override suspend fun decrypt(context: FieldEncryptionContext, value: ByteArray, offset: Int, length: Int) = decrypt(value, offset, length)
}
