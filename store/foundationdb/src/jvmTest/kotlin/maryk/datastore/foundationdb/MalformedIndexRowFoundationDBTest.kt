package maryk.datastore.foundationdb

import kotlinx.coroutines.test.runTest
import maryk.core.clock.HLC
import maryk.core.models.graph
import maryk.core.query.orders.Orders
import maryk.core.query.orders.ascending
import maryk.core.query.requests.add
import maryk.core.query.requests.scan
import maryk.core.query.responses.FetchByIndexScan
import maryk.core.query.responses.statuses.AddSuccess
import maryk.datastore.foundationdb.processors.helpers.awaitResult
import maryk.datastore.foundationdb.processors.helpers.encodeZeroFreeUsing01
import maryk.datastore.foundationdb.processors.helpers.packKey
import maryk.datastore.foundationdb.processors.helpers.packVersionedKey
import maryk.datastore.foundationdb.processors.helpers.toReversedVersionBytes
import maryk.test.models.Person
import maryk.test.models.Person.firstName
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.time.Duration.Companion.minutes
import kotlin.uuid.Uuid

class MalformedIndexRowFoundationDBTest {
    @Test
    fun malformedCompositeIndexRowIsSkippedDuringScan() = runTest(timeout = 3.minutes) {
        val store = FoundationDBDataStore.open(
            fdbClusterFilePath = "./fdb.cluster",
            directoryPath = listOf("maryk", "test", "malformed-index-row-scan", Uuid.random().toString()),
            dataModelsById = mapOf(1u to Person),
            keepAllVersions = true,
        )

        try {
            val person = Person.create {
                firstName with "Jurriaan"
                surname with "Mous"
            }

            val addStatus = assertIs<AddSuccess<Person>>(store.execute(Person.add(person)).statuses.single())
            val index = Person.Meta.indexes!!.single()
            val tableDirs = store.getTableDirs(Person)

            // Value bytes with no decodable trailing varints. This used to abort the whole scan.
            val malformedValueAndKey = byteArrayOf('a'.code.toByte(), 0x80.toByte()) + addStatus.key.bytes

            store.runTransaction { tr ->
                tr.set(
                    packKey(tableDirs.indexPrefix, index.referenceStorageByteArray.bytes, malformedValueAndKey),
                    byteArrayOf()
                )
            }

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
        } finally {
            store.close()
        }
    }

    @Test
    fun malformedHistoricCompositeIndexRowIsSkippedDuringHistoricScan() = runTest(timeout = 3.minutes) {
        val store = FoundationDBDataStore.open(
            fdbClusterFilePath = "./fdb.cluster",
            directoryPath = listOf("maryk", "test", "malformed-historic-index-row-scan", Uuid.random().toString()),
            dataModelsById = mapOf(1u to Person),
            keepAllVersions = true,
        )

        try {
            val person = Person.create {
                firstName with "Jurriaan"
                surname with "Mous"
            }

            val addStatus = assertIs<AddSuccess<Person>>(store.execute(Person.add(person)).statuses.single())
            val index = Person.Meta.indexes!!.single()
            val tableDirs = store.getTableDirs(Person) as HistoricTableDirectories

            val malformedValueAndKey = byteArrayOf('a'.code.toByte(), 0x80.toByte()) + addStatus.key.bytes
            val malformedQualifier = encodeZeroFreeUsing01(
                index.referenceStorageByteArray.bytes + malformedValueAndKey
            )

            store.runTransaction { tr ->
                tr.set(
                    packVersionedKey(
                        tableDirs.historicIndexPrefix,
                        malformedQualifier,
                        version = addStatus.version.toReversedVersionBytes()
                    ),
                    byteArrayOf()
                )
            }

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
        } finally {
            store.close()
        }
    }

    @Test
    fun malformedCurrentIndexedValueDoesNotBreakIndexStartKeyScan() = runTest(timeout = 3.minutes) {
        val store = FoundationDBDataStore.open(
            fdbClusterFilePath = "./fdb.cluster",
            directoryPath = listOf("maryk", "test", "malformed-current-indexed-start-key-scan", Uuid.random().toString()),
            dataModelsById = mapOf(1u to Person),
            keepAllVersions = true,
        )

        try {
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

            val tableDirs = store.getTableDirs(Person)
            val surnameValueKey = packKey(
                tableDirs.tablePrefix,
                second.bytes + Person { surname::ref }.toStorageByteArray()
            )

            store.runTransaction { tr ->
                val currentSurname = tr.get(surnameValueKey).awaitResult()!!
                tr.set(surnameValueKey, currentSurname + byteArrayOf(1))
            }

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
        } finally {
            store.close()
        }
    }

    @Test
    fun malformedHistoricIndexedValueDoesNotBreakHistoricIndexStartKeyScan() = runTest(timeout = 3.minutes) {
        val store = FoundationDBDataStore.open(
            fdbClusterFilePath = "./fdb.cluster",
            directoryPath = listOf("maryk", "test", "malformed-historic-indexed-start-key-scan", Uuid.random().toString()),
            dataModelsById = mapOf(1u to Person),
            keepAllVersions = true,
        )

        try {
            assertIs<AddSuccess<Person>>(store.execute(Person.add(Person.create {
                firstName with "A"
                surname with "Able"
            })).statuses.single())
            val secondStatus = assertIs<AddSuccess<Person>>(store.execute(Person.add(Person.create {
                firstName with "B"
                surname with "Baker"
            })).statuses.single())

            val tableDirs = store.getTableDirs(Person) as HistoricTableDirectories
            val historicSurnameKey = packVersionedKey(
                tableDirs.historicTablePrefix,
                secondStatus.key.bytes,
                encodeZeroFreeUsing01(Person { surname::ref }.toStorageByteArray()),
                version = HLC.toStorageBytes(HLC(secondStatus.version))
            )

            store.runTransaction { tr ->
                val currentHistoricSurname = tr.get(historicSurnameKey).awaitResult()!!
                tr.set(historicSurnameKey, currentHistoricSurname + byteArrayOf(1))
            }

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
        } finally {
            store.close()
        }
    }
}
