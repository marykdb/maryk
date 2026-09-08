package maryk.datastore.rocksdb

import kotlinx.coroutines.test.runTest
import maryk.core.models.RootDataModel
import maryk.core.properties.definitions.StringDefinition
import maryk.core.properties.definitions.list
import maryk.core.properties.definitions.map
import maryk.core.properties.definitions.set
import maryk.core.properties.definitions.string
import maryk.core.properties.exceptions.AlreadyExistsException
import maryk.core.properties.types.Key
import maryk.core.query.changes.Change
import maryk.core.query.changes.ListChange
import maryk.core.query.changes.SetChange
import maryk.core.query.changes.change
import maryk.core.query.pairs.with
import maryk.core.query.requests.add
import maryk.core.query.requests.change
import maryk.core.query.requests.delete
import maryk.core.query.requests.get
import maryk.core.query.responses.statuses.AddSuccess
import maryk.core.query.responses.statuses.ChangeSuccess
import maryk.core.query.responses.statuses.DeleteSuccess
import maryk.core.query.responses.statuses.ValidationFail
import maryk.createTestDBFolder
import maryk.datastore.test.assertStatusIs
import maryk.deleteFolder
import maryk.lib.extensions.compare.matchesRangePart
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull

class RocksDBHistoryAndNestedUniqueRegressionTest {
    @Test
    fun hardDeleteRemovesMaximumKeyQualifiers() = runTest {
        val folder = createTestDBFolder("hard-delete-maximum-key")
        val models = mapOf(1u to OptionalValueModel)
        val key = Key<OptionalValueModel>(ByteArray(OptionalValueModel.Meta.keyByteSize) { 0xff.toByte() })
        var store = RocksDBDataStore.open(
            relativePath = folder,
            dataModelsById = models,
            keepAllVersions = true,
        )

        try {
            assertStatusIs<AddSuccess<OptionalValueModel>>(
                store.execute(
                    OptionalValueModel.add(
                        key to OptionalValueModel.create {
                            requiredValue with "first"
                            optionalValue with "must disappear"
                        }
                    )
                ).statuses.single()
            )
            assertStatusIs<DeleteSuccess<OptionalValueModel>>(
                store.execute(OptionalValueModel.delete(key, hardDelete = true)).statuses.single()
            )

            val columnFamilies = store.getColumnFamilies(OptionalValueModel) as HistoricTableColumnFamilies
            assertNoQualifierWithPrefix(store, columnFamilies.table, key.bytes)
            assertNoQualifierWithPrefix(store, columnFamilies.historic.table, key.bytes)

            store.close()
            store = RocksDBDataStore.open(
                relativePath = folder,
                dataModelsById = models,
                keepAllVersions = true,
            )
            assertStatusIs<AddSuccess<OptionalValueModel>>(
                store.execute(
                    OptionalValueModel.add(
                        key to OptionalValueModel.create { requiredValue with "second" }
                    )
                ).statuses.single()
            )

            assertNull(store.execute(OptionalValueModel.get(key)).values.single().values { optionalValue })
        } finally {
            store.close()
            deleteFolder(folder)
        }
    }

    @Test
    fun historicReadsPreserveExplicitEmptyCollections() = runTest {
        val folder = createTestDBFolder("historic-empty-collections")
        val store = RocksDBDataStore.open(
            relativePath = folder,
            dataModelsById = mapOf(1u to HistoricCollectionsModel),
            keepAllVersions = true,
        )

        try {
            val key = assertStatusIs<AddSuccess<HistoricCollectionsModel>>(
                store.execute(
                    HistoricCollectionsModel.add(
                        HistoricCollectionsModel.create {
                            marker with "initial"
                            listValue with listOf("one")
                            setValue with setOf("one")
                            mapValue with mapOf("one" to "one")
                        }
                    )
                ).statuses.single()
            ).key
            val emptyVersion = assertStatusIs<ChangeSuccess<HistoricCollectionsModel>>(
                store.execute(
                    HistoricCollectionsModel.change(
                        key.change(
                            Change(
                                HistoricCollectionsModel { listValue::ref } with emptyList<String>(),
                                HistoricCollectionsModel { setValue::ref } with emptySet<String>(),
                                HistoricCollectionsModel { mapValue::ref } with emptyMap<String, String>(),
                            )
                        )
                    )
                ).statuses.single()
            ).version
            val laterVersion = assertStatusIs<ChangeSuccess<HistoricCollectionsModel>>(
                store.execute(
                    HistoricCollectionsModel.change(
                        key.change(Change(HistoricCollectionsModel { marker::ref } with "later"))
                    )
                ).statuses.single()
            ).version

            val atEmptyVersion = store.execute(
                HistoricCollectionsModel.get(key, toVersion = emptyVersion)
            ).values.single().values
            assertEquals(emptyList(), atEmptyVersion { listValue })
            assertEquals(emptySet(), atEmptyVersion { setValue })
            assertEquals(emptyMap(), atEmptyVersion { mapValue })

            val afterOtherChange = store.execute(
                HistoricCollectionsModel.get(key, toVersion = laterVersion)
            ).values.single().values
            assertEquals(emptyList(), afterOtherChange { listValue })
            assertEquals(emptySet(), afterOtherChange { setValue })
            assertEquals(emptyMap(), afterOtherChange { mapValue })
        } finally {
            store.close()
            deleteFolder(folder)
        }
    }

    @Test
    fun specializedCollectionChangesRejectNestedUniqueDuplicates() = runTest {
        val folder = createTestDBFolder("specialized-nested-unique")
        val store = RocksDBDataStore.open(
            relativePath = folder,
            dataModelsById = mapOf(1u to NestedUniqueCollectionsModel),
        )

        try {
            val owner = assertStatusIs<AddSuccess<NestedUniqueCollectionsModel>>(
                store.execute(
                    NestedUniqueCollectionsModel.add(
                        NestedUniqueCollectionsModel.create {
                            listValue with listOf("owned-list")
                            setValue with setOf("owned-set")
                        }
                    )
                ).statuses.single()
            )
            val contender = assertStatusIs<AddSuccess<NestedUniqueCollectionsModel>>(
                store.execute(NestedUniqueCollectionsModel.add(NestedUniqueCollectionsModel.create {})).statuses.single()
            )

            val listFailure = assertStatusIs<ValidationFail<NestedUniqueCollectionsModel>>(
                store.execute(
                    NestedUniqueCollectionsModel.change(
                        contender.key.change(
                            ListChange(
                                NestedUniqueCollectionsModel { listValue::ref }.change(
                                    addValuesToEnd = listOf("owned-list")
                                )
                            )
                        )
                    )
                ).statuses.single()
            )
            assertEquals(owner.key, assertIs<AlreadyExistsException>(listFailure.exceptions.single()).key)

            val setFailure = assertStatusIs<ValidationFail<NestedUniqueCollectionsModel>>(
                store.execute(
                    NestedUniqueCollectionsModel.change(
                        contender.key.change(
                            SetChange(
                                NestedUniqueCollectionsModel { setValue::ref }.change(
                                    addValues = setOf("owned-set")
                                )
                            )
                        )
                    )
                ).statuses.single()
            )
            assertEquals(owner.key, assertIs<AlreadyExistsException>(setFailure.exceptions.single()).key)
        } finally {
            store.close()
            deleteFolder(folder)
        }
    }

    @Test
    fun specializedListShiftReleasesOldUniquePosition() = runTest {
        val folder = createTestDBFolder("specialized-list-unique-shift")
        val store = RocksDBDataStore.open(
            relativePath = folder,
            dataModelsById = mapOf(1u to NestedUniqueCollectionsModel),
        )

        try {
            val owner = assertStatusIs<AddSuccess<NestedUniqueCollectionsModel>>(
                store.execute(
                    NestedUniqueCollectionsModel.add(
                        NestedUniqueCollectionsModel.create {
                            listValue with listOf("remove", "shifted")
                        }
                    )
                ).statuses.single()
            )
            assertStatusIs<ChangeSuccess<NestedUniqueCollectionsModel>>(
                store.execute(
                    NestedUniqueCollectionsModel.change(
                        owner.key.change(
                            ListChange(
                                NestedUniqueCollectionsModel { listValue::ref }.change(
                                    deleteValues = listOf("remove")
                                )
                            )
                        )
                    )
                ).statuses.single()
            )

            assertStatusIs<AddSuccess<NestedUniqueCollectionsModel>>(
                store.execute(
                    NestedUniqueCollectionsModel.add(
                        NestedUniqueCollectionsModel.create {
                            listValue with listOf("other", "shifted")
                        }
                    )
                ).statuses.single()
            )
        } finally {
            store.close()
            deleteFolder(folder)
        }
    }

    private fun assertNoQualifierWithPrefix(
        store: RocksDBDataStore,
        columnFamily: maryk.rocksdb.ColumnFamilyHandle,
        prefix: ByteArray,
    ) {
        DBAccessor(store).use { accessor ->
            accessor.getIterator(store.defaultReadOptions, columnFamily).use { iterator ->
                iterator.seek(prefix)
                assertFalse(iterator.isValid() && iterator.key().matchesRangePart(0, prefix))
            }
        }
    }
}

private object OptionalValueModel : RootDataModel<OptionalValueModel>() {
    val requiredValue by string(1u)
    val optionalValue by string(2u, required = false)
}

private object HistoricCollectionsModel : RootDataModel<HistoricCollectionsModel>() {
    val marker by string(1u)
    val listValue by list(2u, required = false, valueDefinition = StringDefinition())
    val setValue by set(3u, required = false, valueDefinition = StringDefinition())
    val mapValue by map(
        4u,
        required = false,
        keyDefinition = StringDefinition(),
        valueDefinition = StringDefinition(),
    )
}

private object NestedUniqueCollectionsModel : RootDataModel<NestedUniqueCollectionsModel>() {
    val listValue by list(
        1u,
        required = false,
        valueDefinition = StringDefinition(unique = true),
    )
    val setValue by set(
        2u,
        required = false,
        valueDefinition = StringDefinition(unique = true),
    )
}
