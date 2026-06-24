package maryk.datastore.rocksdb.processors

import kotlinx.coroutines.test.runTest
import maryk.core.query.changes.Change
import maryk.core.query.changes.change
import maryk.core.query.pairs.with
import maryk.core.query.requests.add
import maryk.core.query.requests.change
import maryk.core.query.responses.statuses.AddSuccess
import maryk.core.query.responses.statuses.ChangeSuccess
import maryk.createTestDBFolder
import maryk.datastore.rocksdb.DBAccessor
import maryk.datastore.rocksdb.HistoricTableColumnFamilies
import maryk.datastore.rocksdb.RocksDBDataStore
import maryk.datastore.rocksdb.processors.helpers.createHistoricIndexKey
import maryk.datastore.rocksdb.processors.helpers.toReversedVersionBytes
import maryk.deleteFolder
import maryk.test.models.AnyValueMapIndexModel
import maryk.test.models.AnyValueSetIndexModel
import maryk.test.models.Person
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class HistoricStoreIndexValuesWalkerTest {
    @Test
    fun walkHistorySkipsTruncatedHistoricPropertyRows() = runTest {
        val folder = createTestDBFolder("historic-index-walker-truncated-row")

        try {
            val store = RocksDBDataStore.open(
                relativePath = folder,
                keepAllVersions = true,
                dataModelsById = mapOf(1u to Person),
            )

            val person = Person.create {
                firstName with "Ada"
                surname with "Lovelace"
            }

            val addStatus = assertIs<AddSuccess<Person>>(
                store.execute(Person.add(person)).statuses.single()
            )

            val changeStatus = assertIs<ChangeSuccess<Person>>(
                store.execute(
                    Person.change(
                        addStatus.key.change(
                            Change(Person.surname.ref() with "Byron")
                        )
                    )
                ).statuses.single()
            )

            val indexable = Person.Meta.indexes!!.single()
            val expected = listOf(
                indexable.toStorageByteArraysForIndex(
                    Person.create {
                        firstName with "Ada"
                        surname with "Byron"
                    },
                    addStatus.key.bytes
                ).single().let {
                    createHistoricIndexKey(indexable.referenceStorageByteArray.bytes, it, changeStatus.version.toReversedVersionBytes())
                },
                indexable.toStorageByteArraysForIndex(
                    person,
                    addStatus.key.bytes
                ).single().let {
                    createHistoricIndexKey(indexable.referenceStorageByteArray.bytes, it, addStatus.version.toReversedVersionBytes())
                }
            )

            val columnFamilies = assertIs<HistoricTableColumnFamilies>(
                store.getColumnFamilies(Person)
            )
            val reference = Person.surname.ref().toStorageByteArray()
            val malformedVersionPrefix = changeStatus.version.toReversedVersionBytes().first()
            val malformedKey = addStatus.key.bytes + reference + byteArrayOf(malformedVersionPrefix)
            store.db.put(columnFamilies.historic.table, malformedKey, "bogus".encodeToByteArray())

            val collected = mutableListOf<ByteArray>()
            DBAccessor(store).use { accessor ->
                HistoricStoreIndexValuesWalker(columnFamilies, store.defaultReadOptions)
                    .walkHistoricalValuesForIndexKeys(addStatus.key.bytes, accessor, indexable) {
                        collected += it
                    }
            }

            assertEquals(expected.map { it.toHexString() }, collected.map { it.toHexString() })

            store.close()
        } finally {
            deleteFolder(folder)
        }
    }

    @Test
    fun walkMapAnyKeyHistorySkipsValueOnlyUpdates() = runTest {
        val folder = createTestDBFolder("historic-map-any-key-walker-update")

        try {
            val store = RocksDBDataStore.open(
                relativePath = folder,
                keepAllVersions = true,
                dataModelsById = mapOf(1u to AnyValueMapIndexModel),
            )

            val addStatus = assertIs<AddSuccess<AnyValueMapIndexModel>>(
                store.execute(
                    AnyValueMapIndexModel.add(
                        AnyValueMapIndexModel.create {
                            name with "walker"
                            mapValues with mapOf("m1" to "v1")
                        }
                    )
                ).statuses.single()
            )

            store.execute(
                AnyValueMapIndexModel.change(
                    addStatus.key.change(
                        Change(
                            AnyValueMapIndexModel { mapValues.refAt("m1") } with "v2"
                        )
                    )
                )
            ).statuses.forEach {
                assertIs<ChangeSuccess<AnyValueMapIndexModel>>(it)
            }

            val indexable = AnyValueMapIndexModel { mapValues.refToAnyKey() }
            val expected = indexable.toStorageByteArraysForIndex(
                AnyValueMapIndexModel.create {
                    name with "walker"
                    mapValues with mapOf("m1" to "value")
                },
                addStatus.key.bytes
            ).single().let {
                createHistoricIndexKey(indexable.referenceStorageByteArray.bytes, it, addStatus.version.toReversedVersionBytes())
            }

            val collected = mutableListOf<ByteArray>()
            val columnFamilies = assertIs<HistoricTableColumnFamilies>(
                store.getColumnFamilies(AnyValueMapIndexModel)
            )
            DBAccessor(store).use { accessor ->
                HistoricStoreIndexValuesWalker(columnFamilies, store.defaultReadOptions)
                    .walkHistoricalValuesForIndexKeys(addStatus.key.bytes, accessor, indexable) {
                        collected += it
                    }
            }

            assertEquals(listOf(expected.toHexString()), collected.map { it.toHexString() })

            store.close()
        } finally {
            deleteFolder(folder)
        }
    }

    @Test
    fun walkMapAnyKeyHistorySkipsDeletedEntries() = runTest {
        val folder = createTestDBFolder("historic-map-any-key-walker-delete")

        try {
            val store = RocksDBDataStore.open(
                relativePath = folder,
                keepAllVersions = true,
                dataModelsById = mapOf(1u to AnyValueMapIndexModel),
            )

            val status = assertIs<AddSuccess<AnyValueMapIndexModel>>(
                store.execute(
                    AnyValueMapIndexModel.add(
                        AnyValueMapIndexModel.create {
                            name with "walker"
                            mapValues with mapOf("m1" to "", "m2" to "v2")
                        }
                    )
                ).statuses.single()
            )
            val key = status.key
            val addVersionBytes = status.version.toReversedVersionBytes()

            store.execute(
                AnyValueMapIndexModel.change(
                    key.change(
                        Change(
                            AnyValueMapIndexModel { mapValues.refAt("m2") } with null
                        )
                    )
                )
            ).statuses.forEach {
                assertIs<ChangeSuccess<AnyValueMapIndexModel>>(it)
            }

            val indexable = AnyValueMapIndexModel { mapValues.refToAnyKey() }
            val expected = listOf("m1", "m2").map { mapKey ->
                val valueAndKey = indexable.toStorageByteArraysForIndex(
                    AnyValueMapIndexModel.create {
                        name with "walker"
                        mapValues with mapOf(mapKey to if (mapKey == "m1") "" else "value")
                    },
                    key.bytes
                ).single()
                createHistoricIndexKey(indexable.referenceStorageByteArray.bytes, valueAndKey, addVersionBytes)
            }

            val collected = mutableListOf<ByteArray>()
            val columnFamilies = assertIs<HistoricTableColumnFamilies>(
                store.getColumnFamilies(AnyValueMapIndexModel)
            )
            DBAccessor(store).use { accessor ->
                HistoricStoreIndexValuesWalker(columnFamilies, store.defaultReadOptions)
                    .walkHistoricalValuesForIndexKeys(key.bytes, accessor, indexable) {
                        collected += it
                    }
            }

            assertEquals(
                expected.map { it.toHexString() }.sorted(),
                collected.map { it.toHexString() }.sorted()
            )

            store.close()
        } finally {
            deleteFolder(folder)
        }
    }

    @Test
    fun walkMapAnyKeyHistorySkipsTruncatedHistoricRows() = runTest {
        val folder = createTestDBFolder("historic-map-any-key-walker-truncated")

        try {
            val store = RocksDBDataStore.open(
                relativePath = folder,
                keepAllVersions = true,
                dataModelsById = mapOf(1u to AnyValueMapIndexModel),
            )

            val values = AnyValueMapIndexModel.create {
                name with "walker"
                mapValues with mapOf("m1" to "v1")
            }

            val status = assertIs<AddSuccess<AnyValueMapIndexModel>>(
                store.execute(AnyValueMapIndexModel.add(values)).statuses.single()
            )
            val key = status.key
            val versionBytes = status.version.toReversedVersionBytes()
            val indexable = AnyValueMapIndexModel { mapValues.refToAnyKey() }

            val columnFamilies = assertIs<HistoricTableColumnFamilies>(
                store.getColumnFamilies(AnyValueMapIndexModel)
            )
            val malformedKey = key.bytes +
                indexable.parentReference!!.toStorageByteArray() +
                byteArrayOf(versionBytes.first())
            store.db.put(columnFamilies.historic.table, malformedKey, "bogus".encodeToByteArray())

            val collected = mutableListOf<ByteArray>()
            DBAccessor(store).use { accessor ->
                HistoricStoreIndexValuesWalker(columnFamilies, store.defaultReadOptions)
                    .walkHistoricalValuesForIndexKeys(key.bytes, accessor, indexable) {
                        collected += it
                    }
            }

            val expected = indexable.toStorageByteArraysForIndex(values, key.bytes).map {
                createHistoricIndexKey(indexable.referenceStorageByteArray.bytes, it, versionBytes)
            }

            assertEquals(
                expected.map { it.toHexString() }.sorted(),
                collected.map { it.toHexString() }.sorted()
            )

            store.close()
        } finally {
            deleteFolder(folder)
        }
    }

    @Test
    fun walkSetAnyValueHistoryUsesFullIndexEncoding() = runTest {
        val folder = createTestDBFolder("historic-set-any-value-walker")

        try {
            val store = RocksDBDataStore.open(
                relativePath = folder,
                keepAllVersions = true,
                dataModelsById = mapOf(1u to AnyValueSetIndexModel),
            )

            val values = AnyValueSetIndexModel.create {
                name with "walker"
                setValues with setOf("s4", "s0")
            }

            val status = assertIs<AddSuccess<AnyValueSetIndexModel>>(
                store.execute(AnyValueSetIndexModel.add(values)).statuses.single()
            )
            val key = status.key
            val versionBytes = status.version.toReversedVersionBytes()
            val indexable = AnyValueSetIndexModel { setValues.refToAny() }

            val columnFamilies = assertIs<HistoricTableColumnFamilies>(
                store.getColumnFamilies(AnyValueSetIndexModel)
            )

            val collected = mutableListOf<ByteArray>()
            DBAccessor(store).use { accessor ->
                HistoricStoreIndexValuesWalker(columnFamilies, store.defaultReadOptions)
                    .walkHistoricalValuesForIndexKeys(key.bytes, accessor, indexable) {
                        collected += it
                    }
            }

            val expected = indexable.toStorageByteArraysForIndex(values, key.bytes).map {
                createHistoricIndexKey(indexable.referenceStorageByteArray.bytes, it, versionBytes)
            }

            assertEquals(
                expected.map { it.toHexString() }.sorted(),
                collected.map { it.toHexString() }.sorted()
            )

            store.close()
        } finally {
            deleteFolder(folder)
        }
    }

    @Test
    fun walkSetAnyValueHistorySkipsTruncatedHistoricRows() = runTest {
        val folder = createTestDBFolder("historic-set-any-value-walker-truncated")

        try {
            val store = RocksDBDataStore.open(
                relativePath = folder,
                keepAllVersions = true,
                dataModelsById = mapOf(1u to AnyValueSetIndexModel),
            )

            val values = AnyValueSetIndexModel.create {
                name with "walker"
                setValues with setOf("s4", "s0")
            }

            val status = assertIs<AddSuccess<AnyValueSetIndexModel>>(
                store.execute(AnyValueSetIndexModel.add(values)).statuses.single()
            )
            val key = status.key
            val versionBytes = status.version.toReversedVersionBytes()
            val indexable = AnyValueSetIndexModel { setValues.refToAny() }

            val columnFamilies = assertIs<HistoricTableColumnFamilies>(
                store.getColumnFamilies(AnyValueSetIndexModel)
            )
            val malformedKey = key.bytes +
                indexable.parentReference!!.toStorageByteArray() +
                byteArrayOf(versionBytes.first())
            store.db.put(columnFamilies.historic.table, malformedKey, "bogus".encodeToByteArray())

            val collected = mutableListOf<ByteArray>()
            DBAccessor(store).use { accessor ->
                HistoricStoreIndexValuesWalker(columnFamilies, store.defaultReadOptions)
                    .walkHistoricalValuesForIndexKeys(key.bytes, accessor, indexable) {
                        collected += it
                    }
            }

            val expected = indexable.toStorageByteArraysForIndex(values, key.bytes).map {
                createHistoricIndexKey(indexable.referenceStorageByteArray.bytes, it, versionBytes)
            }

            assertEquals(
                expected.map { it.toHexString() }.sorted(),
                collected.map { it.toHexString() }.sorted()
            )

            store.close()
        } finally {
            deleteFolder(folder)
        }
    }

    @Test
    fun walkSetAnyValueHistorySkipsDeletedEntries() = runTest {
        val folder = createTestDBFolder("historic-set-any-value-walker-delete")

        try {
            val store = RocksDBDataStore.open(
                relativePath = folder,
                keepAllVersions = true,
                dataModelsById = mapOf(1u to AnyValueSetIndexModel),
            )

            val status = assertIs<AddSuccess<AnyValueSetIndexModel>>(
                store.execute(
                    AnyValueSetIndexModel.add(
                        AnyValueSetIndexModel.create {
                            name with "walker"
                            setValues with setOf("s1", "s2")
                        }
                    )
                ).statuses.single()
            )
            val key = status.key
            val addVersionBytes = status.version.toReversedVersionBytes()

            store.execute(
                AnyValueSetIndexModel.change(
                    key.change(
                        Change(
                            AnyValueSetIndexModel { setValues.refAt("s1") } with null
                        )
                    )
                )
            ).statuses.forEach {
                assertIs<ChangeSuccess<AnyValueSetIndexModel>>(it)
            }

            val indexable = AnyValueSetIndexModel { setValues.refToAny() }
            val expected = listOf("s1", "s2").map { setItem ->
                val valueAndKey = indexable.toStorageByteArraysForIndex(
                    AnyValueSetIndexModel.create {
                        name with "walker"
                        setValues with setOf(setItem)
                    },
                    key.bytes
                ).single()
                createHistoricIndexKey(indexable.referenceStorageByteArray.bytes, valueAndKey, addVersionBytes)
            }

            val collected = mutableListOf<ByteArray>()
            val columnFamilies = assertIs<HistoricTableColumnFamilies>(
                store.getColumnFamilies(AnyValueSetIndexModel)
            )
            DBAccessor(store).use { accessor ->
                HistoricStoreIndexValuesWalker(columnFamilies, store.defaultReadOptions)
                    .walkHistoricalValuesForIndexKeys(key.bytes, accessor, indexable) {
                        collected += it
                    }
            }

            assertEquals(
                expected.map { it.toHexString() }.sorted(),
                collected.map { it.toHexString() }.sorted()
            )

            store.close()
        } finally {
            deleteFolder(folder)
        }
    }
}
