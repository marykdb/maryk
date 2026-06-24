package maryk.datastore.foundationdb.processors

import kotlinx.coroutines.test.runTest
import maryk.core.query.changes.Change
import maryk.core.query.changes.change
import maryk.core.query.pairs.with
import maryk.core.query.requests.add
import maryk.core.query.requests.change
import maryk.core.query.responses.statuses.AddSuccess
import maryk.core.query.responses.statuses.ChangeSuccess
import maryk.datastore.foundationdb.FoundationDBDataStore
import maryk.datastore.foundationdb.HistoricTableDirectories
import maryk.datastore.foundationdb.processors.helpers.encodeZeroFreeUsing01
import maryk.datastore.foundationdb.processors.helpers.packKey
import maryk.datastore.foundationdb.processors.helpers.toReversedVersionBytes
import maryk.lib.bytes.combineToByteArray
import maryk.test.models.AnyValueMapIndexModel
import maryk.test.models.AnyValueSetIndexModel
import maryk.test.models.Person
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.time.Duration.Companion.minutes
import kotlin.uuid.Uuid

class HistoricStoreIndexValuesWalkerTest {
    @Test
    fun walkHistorySkipsMalformedHistoricPropertyRows() = runTest(timeout = 3.minutes) {
        val store = FoundationDBDataStore.open(
            fdbClusterFilePath = "./fdb.cluster",
            directoryPath = listOf("maryk", "test", "historic-index-walker-malformed-property", Uuid.random().toString()),
            dataModelsById = mapOf(1u to Person),
            keepAllVersions = true,
        )

        try {
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
                    combineToByteArray(it, changeStatus.version.toReversedVersionBytes())
                },
                indexable.toStorageByteArraysForIndex(
                    person,
                    addStatus.key.bytes
                ).single().let {
                    combineToByteArray(it, addStatus.version.toReversedVersionBytes())
                }
            )

            val tableDirs = assertIs<HistoricTableDirectories>(store.getTableDirs(Person))
            val reference = Person.surname.ref().toStorageByteArray()
            store.runTransaction { tr ->
                tr.set(
                    packKey(
                        tableDirs.historicTablePrefix,
                        addStatus.key.bytes,
                        encodeZeroFreeUsing01(reference)
                    ),
                    byteArrayOf(99)
                )
            }

            val collected = mutableListOf<ByteArray>()
            store.runTransaction { tr ->
                HistoricStoreIndexValuesWalker(tableDirs).walkHistoricalValuesForIndexKeys(
                    addStatus.key.bytes,
                    tr,
                    indexable
                ) { valueAndKey, version ->
                    collected += combineToByteArray(valueAndKey, version.toReversedVersionBytes())
                }
            }

            assertEquals(expected.map { it.toHexString() }, collected.map { it.toHexString() })
        } finally {
            store.close()
        }
    }

    @Test
    fun walkMapAnyKeyHistorySkipsValueOnlyUpdates() = runTest(timeout = 3.minutes) {
        val store = FoundationDBDataStore.open(
            fdbClusterFilePath = "./fdb.cluster",
            directoryPath = listOf("maryk", "test", "historic-map-any-key-walker-update", Uuid.random().toString()),
            dataModelsById = mapOf(1u to AnyValueMapIndexModel),
            keepAllVersions = true,
        )

        try {
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
            ).single() + addStatus.version.toReversedVersionBytes()

            val collected = mutableListOf<ByteArray>()
            val tableDirs = assertIs<HistoricTableDirectories>(store.getTableDirs(AnyValueMapIndexModel))
            store.runTransaction { tr ->
                HistoricStoreIndexValuesWalker(tableDirs).walkHistoricalValuesForIndexKeys(
                    addStatus.key.bytes,
                    tr,
                    indexable
                ) { valueAndKey, version ->
                    collected += valueAndKey + version.toReversedVersionBytes()
                }
            }

            assertEquals(listOf(expected.toHexString()), collected.map { it.toHexString() })
        } finally {
            store.close()
        }
    }

    @Test
    fun walkMapAnyKeyHistorySkipsDeletedEntries() = runTest(timeout = 3.minutes) {
        val store = FoundationDBDataStore.open(
            fdbClusterFilePath = "./fdb.cluster",
            directoryPath = listOf("maryk", "test", "historic-map-any-key-walker-delete", Uuid.random().toString()),
            dataModelsById = mapOf(1u to AnyValueMapIndexModel),
            keepAllVersions = true,
        )

        try {
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
                combineToByteArray(valueAndKey, addVersionBytes)
            }

            val collected = mutableListOf<ByteArray>()
            val tableDirs = assertIs<HistoricTableDirectories>(store.getTableDirs(AnyValueMapIndexModel))
            store.runTransaction { tr ->
                HistoricStoreIndexValuesWalker(tableDirs).walkHistoricalValuesForIndexKeys(
                    key.bytes,
                    tr,
                    indexable
                ) { valueAndKey, _ ->
                    collected += valueAndKey + addVersionBytes
                }
            }

            assertEquals(
                expected.map { it.toHexString() }.sorted(),
                collected.map { it.toHexString() }.sorted()
            )
        } finally {
            store.close()
        }
    }

    @Test
    fun walkSetAnyValueHistorySkipsDeletedEntries() = runTest(timeout = 3.minutes) {
        val store = FoundationDBDataStore.open(
            fdbClusterFilePath = "./fdb.cluster",
            directoryPath = listOf("maryk", "test", "historic-set-any-value-walker-delete", Uuid.random().toString()),
            dataModelsById = mapOf(1u to AnyValueSetIndexModel),
            keepAllVersions = true,
        )

        try {
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
                combineToByteArray(valueAndKey, addVersionBytes)
            }

            val collected = mutableListOf<ByteArray>()
            val tableDirs = assertIs<HistoricTableDirectories>(store.getTableDirs(AnyValueSetIndexModel))
            store.runTransaction { tr ->
                HistoricStoreIndexValuesWalker(tableDirs).walkHistoricalValuesForIndexKeys(
                    key.bytes,
                    tr,
                    indexable
                ) { valueAndKey, _ ->
                    collected += valueAndKey + addVersionBytes
                }
            }

            assertEquals(
                expected.map { it.toHexString() }.sorted(),
                collected.map { it.toHexString() }.sorted()
            )
        } finally {
            store.close()
        }
    }
}
