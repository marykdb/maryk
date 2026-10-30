package maryk.datastore.rocksdb

import kotlinx.coroutines.test.runTest
import maryk.core.models.RootDataModel
import maryk.core.models.DataModel
import maryk.core.models.graph
import maryk.core.properties.definitions.StringDefinition
import maryk.core.properties.definitions.index.AnyOf
import maryk.core.properties.definitions.index.Multiple
import maryk.core.properties.definitions.map
import maryk.core.properties.definitions.embed
import maryk.core.properties.graph.graph
import maryk.core.properties.definitions.set
import maryk.core.properties.definitions.string
import maryk.core.query.changes.Change
import maryk.core.query.changes.ObjectSoftDeleteChange
import maryk.core.query.changes.change
import maryk.core.query.filters.And
import maryk.core.query.filters.Equals
import maryk.core.query.filters.Matches
import maryk.core.query.pairs.with
import maryk.core.query.requests.add
import maryk.core.query.requests.change
import maryk.core.query.requests.delete
import maryk.core.query.requests.scan
import maryk.core.query.responses.FetchByIndexScan
import maryk.core.query.responses.statuses.AddSuccess
import maryk.core.query.responses.statuses.ChangeSuccess
import maryk.core.query.responses.statuses.DeleteSuccess
import maryk.createTestDBFolder
import maryk.datastore.rocksdb.metadata.LEGACY_INDEX_KEY_FORMAT_VERSION
import maryk.datastore.rocksdb.metadata.readStoreMetaFile
import maryk.datastore.rocksdb.metadata.writeStoreMetaFile
import maryk.deleteFolder
import maryk.datastore.shared.encryption.AesGcmHmacSha256EncryptionProvider
import maryk.datastore.shared.encryption.ContextualFieldEncryptionProvider
import maryk.datastore.shared.encryption.FieldEncryptionContext
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

private object RebuiltFanoutModel : RootDataModel<RebuiltFanoutModel>(
    indexes = {
        listOf(
            RebuiltFanoutModel.ref { setValues.any() },
            RebuiltFanoutModel.ref { mapValues.anyKey() },
            AnyOf("search", RebuiltFanoutModel.first.ref(), RebuiltFanoutModel.second.ref()),
            Multiple(RebuiltFanoutModel.name.ref(), RebuiltFanoutModel.ref { setValues.any() }),
        )
    },
) {
    val name by string(1u)
    val first by string(2u, required = false)
    val second by string(3u, required = false)
    val setValues by set(4u, required = false, valueDefinition = StringDefinition())
    val mapValues by map(5u, required = false, keyDefinition = StringDefinition(), valueDefinition = StringDefinition())
}

private object RebuildSecretChild : DataModel<RebuildSecretChild>() {
    val value by string(1u)
    val secret by string(2u, sensitive = true)
}

private object RebuildSecretModel : RootDataModel<RebuildSecretModel>(indexes = {
    listOf(RebuildSecretModel.name.ref(), RebuildSecretModel.ref { child { value } })
}) {
    val name by string(1u)
    val secret by string(2u, sensitive = true)
    val child by embed(3u, dataModel = { RebuildSecretChild })
}

class RocksDBIndexRebuildHistoryTest {
    @Test
    fun plaintextIndexRebuildDoesNotDecryptUnrelatedRootOrEmbeddedFields() = runTest {
        val folder = createTestDBFolder("rebuild-plaintext-with-unavailable-secret-key")
        val provider = AesGcmHmacSha256EncryptionProvider(ByteArray(32) { it.toByte() }, ByteArray(32) { (it + 32).toByte() })
        var store = RocksDBDataStore.open(
            relativePath = folder, dataModelsById = mapOf(1u to RebuildSecretModel),
            keepAllVersions = true, fieldEncryptionProvider = provider,
        )
        try {
            val added = assertIs<AddSuccess<RebuildSecretModel>>(store.execute(RebuildSecretModel.add(
                RebuildSecretModel.create {
                    name with "plain"
                    secret with "root secret"
                    child with RebuildSecretChild.create { value with "nested"; secret with "child secret" }
                },
            )).statuses.single())
            store.close()
            val metadata = readStoreMetaFile(folder)
            writeStoreMetaFile(folder, metadata.copy(indexKeyFormatVersion = LEGACY_INDEX_KEY_FORMAT_VERSION))
            val unavailableKeys = object : ContextualFieldEncryptionProvider by provider {
                override suspend fun decrypt(value: ByteArray, offset: Int, length: Int): ByteArray =
                    error("Index rebuild must not decrypt unrelated fields")
                override suspend fun decrypt(context: FieldEncryptionContext, value: ByteArray, offset: Int, length: Int): ByteArray =
                    error("Index rebuild must not decrypt unrelated fields")
            }
            store = RocksDBDataStore.open(
                relativePath = folder, dataModelsById = mapOf(1u to RebuildSecretModel),
                keepAllVersions = true, fieldEncryptionProvider = unavailableKeys,
            )
            val select = RebuildSecretModel.graph { listOf(name, graph(child) { listOf(value) }) }
            for (version in listOf(null, added.version)) {
                for (filter in listOf(
                    Equals(RebuildSecretModel.name.ref() with "plain"),
                    Equals(RebuildSecretModel.ref { child { value } } with "nested"),
                )) {
                    val response = store.execute(RebuildSecretModel.scan(where = filter, select = select, toVersion = version))
                    assertIs<FetchByIndexScan>(response.dataFetchType)
                    assertEquals(listOf(added.key), response.values.map { it.key })
                }
            }
        } finally {
            store.close()
            deleteFolder(folder)
        }
    }

    @Test
    fun legacyRebuildPreservesIndependentFanoutAndDeletionHistory() = runTest {
        val folder = createTestDBFolder("rebuild-fanout-history")
        suspend fun open() = RocksDBDataStore.open(
            relativePath = folder, dataModelsById = mapOf(1u to RebuiltFanoutModel), keepAllVersions = true,
        )
        var store = open()
        try {
            val added = assertIs<AddSuccess<RebuiltFanoutModel>>(store.execute(RebuiltFanoutModel.add(
                RebuiltFanoutModel.create {
                    name with "record"
                    first with "first"
                    second with "retained"
                    setValues with setOf("s2")
                    mapValues with mapOf("m2" to "retained")
                },
            )).statuses.single())
            val expanded = assertIs<ChangeSuccess<RebuiltFanoutModel>>(store.execute(RebuiltFanoutModel.change(
                added.key.change(Change(
                    RebuiltFanoutModel.first.ref() with "changed",
                    RebuiltFanoutModel.setValues.ref() with setOf("s1", "s2"),
                    RebuiltFanoutModel.mapValues.ref() with mapOf("m1" to "new", "m2" to "retained"),
                )),
            )).statuses.single())
            val contracted = assertIs<ChangeSuccess<RebuiltFanoutModel>>(store.execute(RebuiltFanoutModel.change(
                added.key.change(Change(
                    RebuiltFanoutModel.first.ref() with null,
                    RebuiltFanoutModel.setValues.ref() with setOf("s2"),
                    RebuiltFanoutModel.mapValues.ref() with mapOf("m2" to "retained"),
                )),
            )).statuses.single())
            val deleted = assertIs<DeleteSuccess<RebuiltFanoutModel>>(
                store.execute(RebuiltFanoutModel.delete(added.key)).statuses.single(),
            )
            val restored = assertIs<ChangeSuccess<RebuiltFanoutModel>>(store.execute(RebuiltFanoutModel.change(
                added.key.change(ObjectSoftDeleteChange(false)),
            )).statuses.single())
            val versions = listOf(added.version, expanded.version, contracted.version, deleted.version, restored.version)
            val expected = readStates(store, versions)
            assertEquals(listOf("record"), expected[1][0])
            assertEquals(listOf("record"), expected[1][1])
            assertEquals(emptyList(), expected[3][0])

            store.close()
            val metadata = readStoreMetaFile(folder)
            writeStoreMetaFile(folder, metadata.copy(indexKeyFormatVersion = LEGACY_INDEX_KEY_FORMAT_VERSION))
            store = open()
            assertEquals(expected, readStates(store, versions))
            store.close()
            store = open()
            assertEquals(expected, readStates(store, versions))
        } finally {
            store.close()
            deleteFolder(folder)
        }
    }

    private suspend fun readStates(store: RocksDBDataStore, versions: List<ULong>) = versions.map { version ->
        listOf(
            Equals(RebuiltFanoutModel.ref { setValues.any() } with "s2"),
            Equals(RebuiltFanoutModel.ref { mapValues.anyKey() } with "m2"),
            Matches("search" with "retained"),
            And(
                Equals(RebuiltFanoutModel.name.ref() with "record"),
                Equals(RebuiltFanoutModel.ref { setValues.any() } with "s2"),
            ),
        ).map { filter ->
            val response = store.execute(RebuiltFanoutModel.scan(where = filter, toVersion = version))
            assertIs<FetchByIndexScan>(response.dataFetchType)
            response.values.map { it.values { name } }
        }
    }
}
