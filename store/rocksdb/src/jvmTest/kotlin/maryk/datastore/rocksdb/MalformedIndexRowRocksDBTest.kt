package maryk.datastore.rocksdb

import kotlinx.coroutines.test.runTest
import maryk.core.clock.HLC
import maryk.core.extensions.bytes.invert
import maryk.core.models.graph
import maryk.core.query.orders.Orders
import maryk.core.query.orders.ascending
import maryk.core.properties.types.Bytes
import maryk.core.query.requests.add
import maryk.core.query.requests.scan
import maryk.core.query.requests.scanUpdates
import maryk.core.query.responses.FetchByIndexScan
import maryk.core.query.responses.statuses.AddSuccess
import maryk.core.query.responses.updates.OrderedKeysUpdate
import maryk.createTestDBFolder
import maryk.lib.bytes.combineToByteArray
import maryk.datastore.rocksdb.processors.helpers.createIndexKey
import maryk.datastore.rocksdb.processors.helpers.createHistoricIndexKey
import maryk.datastore.rocksdb.processors.helpers.toReversedVersionBytes
import maryk.deleteFolder
import maryk.test.models.Person
import maryk.test.models.Person.firstName
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class MalformedIndexRowRocksDBTest {
    @Test
    fun malformedCompositeIndexRowIsSkippedDuringScan() = runTest {
        val folder = createTestDBFolder("malformed-index-row-scan")

        try {
            val store = RocksDBDataStore.open(
                relativePath = folder,
                keepAllVersions = true,
                dataModelsById = mapOf(1u to Person),
            )

            val person = Person.create {
                firstName with "Jurriaan"
                surname with "Mous"
            }

            val addStatus = assertIs<AddSuccess<Person>>(store.execute(Person.add(person)).statuses.single())
            val index = Person.Meta.indexes!!.single()
            val columnFamilies = store.getColumnFamilies(Person)

            // Value bytes with no decodable trailing varints. Scan should skip this row.
            val malformedValueAndKey = byteArrayOf('a'.code.toByte(), 0x80.toByte()) + addStatus.key.bytes

            store.db.put(
                columnFamilies.index,
                createIndexKey(index.referenceStorageByteArray.bytes, malformedValueAndKey),
                byteArrayOf()
            )

            val scanResponse = store.execute(
                Person.scan(
                    order = Orders(
                        Person { surname::ref }.ascending(),
                        Person { firstName::ref }.ascending()
                    )
                )
            )

            assertIs<FetchByIndexScan>(scanResponse.dataFetchType)
            assertEquals(1, scanResponse.values.size)
            assertEquals(person, scanResponse.values.single().values)
            assertEquals(addStatus.key, scanResponse.values.single().key)

            store.close()
        } finally {
            deleteFolder(folder)
        }
    }

    @Test
    fun danglingCurrentIndexRowDoesNotConsumeScanLimit() = runTest {
        val folder = createTestDBFolder("dangling-index-row-scan-limit")

        try {
            val store = RocksDBDataStore.open(
                relativePath = folder,
                keepAllVersions = true,
                dataModelsById = mapOf(1u to Person),
            )

            val person = Person.create {
                firstName with "A"
                surname with "Able"
            }

            val addStatus = assertIs<AddSuccess<Person>>(store.execute(Person.add(person)).statuses.single())
            val index = Person.Meta.indexes!!.single()
            val columnFamilies = store.getColumnFamilies(Person)
            val danglingKey = ByteArray(addStatus.key.bytes.size)
            val danglingValueAndKey = index.toStorageByteArraysForIndex(
                Person.create {
                    firstName with "A"
                    surname with "Aardvark"
                },
                danglingKey
            ).single()

            store.db.put(
                columnFamilies.index,
                createIndexKey(index.referenceStorageByteArray.bytes, danglingValueAndKey),
                byteArrayOf()
            )

            val scanResponse = store.execute(
                Person.scan(
                    order = Orders(
                        Person { surname::ref }.ascending(),
                        Person { firstName::ref }.ascending()
                    ),
                    limit = 1u
                )
            )

            assertIs<FetchByIndexScan>(scanResponse.dataFetchType)
            assertEquals(listOf(addStatus.key), scanResponse.values.map { it.key })
            assertEquals(listOf(person), scanResponse.values.map { it.values })

            store.close()
        } finally {
            deleteFolder(folder)
        }
    }

    @Test
    fun malformedHistoricCompositeIndexRowIsSkippedDuringHistoricScan() = runTest {
        val folder = createTestDBFolder("malformed-historic-index-row-scan")

        try {
            val store = RocksDBDataStore.open(
                relativePath = folder,
                keepAllVersions = true,
                dataModelsById = mapOf(1u to Person),
            )

            val person = Person.create {
                firstName with "Jurriaan"
                surname with "Mous"
            }

            val addStatus = assertIs<AddSuccess<Person>>(store.execute(Person.add(person)).statuses.single())
            val index = Person.Meta.indexes!!.single()
            val columnFamilies = store.getColumnFamilies(Person) as HistoricTableColumnFamilies

            val malformedValueAndKey = byteArrayOf('a'.code.toByte(), 0x80.toByte()) + addStatus.key.bytes
            val version = addStatus.version.toReversedVersionBytes()
            val malformedHistoricKey = createHistoricIndexKey(
                index.referenceStorageByteArray.bytes,
                malformedValueAndKey,
                version
            ).also {
                it.invert(it.size - version.size)
            }

            store.db.put(columnFamilies.historic.index, malformedHistoricKey, byteArrayOf())

            val scanResponse = store.execute(
                Person.scan(
                    toVersion = addStatus.version,
                    order = Orders(
                        Person { surname::ref }.ascending(),
                        Person { firstName::ref }.ascending()
                    )
                )
            )

            assertIs<FetchByIndexScan>(scanResponse.dataFetchType)
            assertEquals(1, scanResponse.values.size)
            assertEquals(person, scanResponse.values.single().values)
            assertEquals(addStatus.key, scanResponse.values.single().key)

            store.close()
        } finally {
            deleteFolder(folder)
        }
    }

    @Test
    fun malformedCurrentIndexedValueDoesNotBreakIndexStartKeyScan() = runTest {
        val folder = createTestDBFolder("malformed-current-indexed-start-key-scan")

        try {
            val store = RocksDBDataStore.open(
                relativePath = folder,
                keepAllVersions = true,
                dataModelsById = mapOf(1u to Person),
            )

            assertIs<AddSuccess<Person>>(store.execute(Person.add(Person.create {
                firstName with "A"
                surname with "Able"
            })).statuses.single())
            val second = assertIs<AddSuccess<Person>>(store.execute(Person.add(Person.create {
                firstName with "B"
                surname with "Baker"
            })).statuses.single()).key
            val third = assertIs<AddSuccess<Person>>(store.execute(Person.add(Person.create {
                firstName with "C"
                surname with "Clark"
            })).statuses.single()).key

            val columnFamilies = store.getColumnFamilies(Person)
            val surnameValueKey = second.bytes + Person { surname::ref }.toStorageByteArray()
            val currentSurname = store.db.get(columnFamilies.table, surnameValueKey)!!
            store.db.put(columnFamilies.table, surnameValueKey, currentSurname + byteArrayOf(1))

            val scanResponse = store.execute(
                Person.scan(
                    startKey = second,
                    order = Orders(
                        Person { surname::ref }.ascending(),
                        Person { firstName::ref }.ascending()
                    ),
                    select = Person.graph { listOf(firstName) }
                )
            )

            assertIs<FetchByIndexScan>(scanResponse.dataFetchType)
            assertEquals(listOf(second, third), scanResponse.values.map { it.key })
            assertEquals(listOf("B", "C"), scanResponse.values.map { it.values { firstName } })

            store.close()
        } finally {
            deleteFolder(folder)
        }
    }

    @Test
    fun malformedCurrentIndexedValueDoesNotBreakOrderedScanUpdates() = runTest {
        val folder = createTestDBFolder("malformed-current-indexed-scan-updates")

        try {
            val store = RocksDBDataStore.open(
                relativePath = folder,
                keepAllVersions = true,
                dataModelsById = mapOf(1u to Person),
            )

            val firstPerson = Person.create {
                firstName with "A"
                surname with "Able"
            }
            val first = assertIs<AddSuccess<Person>>(store.execute(Person.add(firstPerson)).statuses.single()).key
            val secondPerson = Person.create {
                firstName with "B"
                surname with "Baker"
            }
            val second = assertIs<AddSuccess<Person>>(store.execute(Person.add(secondPerson)).statuses.single()).key

            val columnFamilies = store.getColumnFamilies(Person)
            val surnameValueKey = second.bytes + Person { surname::ref }.toStorageByteArray()
            val currentSurname = store.db.get(columnFamilies.table, surnameValueKey)!!
            store.db.put(columnFamilies.table, surnameValueKey, currentSurname + byteArrayOf(1))

            val updatesResponse = store.execute(
                Person.scanUpdates(
                    order = Orders(
                        Person { surname::ref }.ascending(),
                        Person { firstName::ref }.ascending()
                    ),
                    select = Person.graph { listOf(firstName) },
                    limit = 2u,
                )
            )

            val index = Person.Meta.indexes!!.single()
            assertIs<OrderedKeysUpdate<Person>>(updatesResponse.updates.first()).apply {
                assertEquals(listOf(first, second), keys)
                assertEquals(
                    listOf(
                        Bytes(index.toStorageByteArraysForIndex(firstPerson, first.bytes).single()),
                        Bytes(index.toStorageByteArraysForIndex(secondPerson, second.bytes).single())
                    ),
                    sortingKeys
                )
            }

            store.close()
        } finally {
            deleteFolder(folder)
        }
    }

    @Test
    fun malformedHistoricIndexedValueDoesNotBreakHistoricIndexStartKeyScan() = runTest {
        val folder = createTestDBFolder("malformed-historic-indexed-start-key-scan")

        try {
            val store = RocksDBDataStore.open(
                relativePath = folder,
                keepAllVersions = true,
                dataModelsById = mapOf(1u to Person),
            )

            assertIs<AddSuccess<Person>>(store.execute(Person.add(Person.create {
                firstName with "A"
                surname with "Able"
            })).statuses.single())
            val secondStatus = assertIs<AddSuccess<Person>>(store.execute(Person.add(Person.create {
                firstName with "B"
                surname with "Baker"
            })).statuses.single())

            val columnFamilies = store.getColumnFamilies(Person) as HistoricTableColumnFamilies
            val versionBytes = HLC.toStorageBytes(HLC(secondStatus.version))
            val historicSurnameKey = combineToByteArray(
                secondStatus.key.bytes,
                Person { surname::ref }.toStorageByteArray(),
                versionBytes
            ).apply {
                invert(size - versionBytes.size)
            }
            val currentHistoricSurname = store.db.get(columnFamilies.historic.table, historicSurnameKey)!!
            store.db.put(columnFamilies.historic.table, historicSurnameKey, currentHistoricSurname + byteArrayOf(1))

            val scanResponse = store.execute(
                Person.scan(
                    startKey = secondStatus.key,
                    toVersion = secondStatus.version,
                    order = Orders(
                        Person { surname::ref }.ascending(),
                        Person { firstName::ref }.ascending()
                    ),
                    select = Person.graph { listOf(firstName) }
                )
            )

            assertIs<FetchByIndexScan>(scanResponse.dataFetchType)
            assertEquals(listOf(secondStatus.key), scanResponse.values.map { it.key })
            assertEquals(listOf("B"), scanResponse.values.map { it.values { firstName } })

            store.close()
        } finally {
            deleteFolder(folder)
        }
    }
}
