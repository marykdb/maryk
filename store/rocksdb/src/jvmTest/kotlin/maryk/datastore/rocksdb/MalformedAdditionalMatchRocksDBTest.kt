package maryk.datastore.rocksdb

import kotlinx.coroutines.test.runTest
import maryk.core.extensions.bytes.invert
import maryk.core.query.filters.MatchesPrefix
import maryk.core.query.pairs.with
import maryk.core.query.requests.add
import maryk.core.query.requests.scan
import maryk.core.query.responses.FetchByIndexScan
import maryk.core.query.responses.statuses.AddSuccess
import maryk.createTestDBFolder
import maryk.datastore.rocksdb.processors.helpers.createHistoricIndexKey
import maryk.datastore.rocksdb.processors.helpers.createIndexKey
import maryk.datastore.rocksdb.processors.helpers.toReversedVersionBytes
import maryk.deleteFolder
import maryk.test.models.CaseInsensitivePerson
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class MalformedAdditionalMatchRocksDBTest {
    @Test
    fun malformedShortAdditionalMatchRowIsSkippedDuringScan() = runTest {
        val folder = createTestDBFolder("malformed-additional-match-scan")

        try {
            val store = RocksDBDataStore.open(
                relativePath = folder,
                keepAllVersions = true,
                dataModelsById = mapOf(1u to CaseInsensitivePerson),
            )

            val person = CaseInsensitivePerson.create {
                firstName with "Jose"
                surname with "Garcia"
            }

            assertIs<AddSuccess<CaseInsensitivePerson>>(store.execute(CaseInsensitivePerson.add(person)).statuses.single())

            val index = CaseInsensitivePerson.Meta.indexes!![1]
            val columnFamilies = store.getColumnFamilies(CaseInsensitivePerson)

            // Shorter than the helper prefix for MatchesPrefix("name" with "gar").
            store.db.put(
                columnFamilies.index,
                createIndexKey(index.referenceStorageByteArray.bytes, "ga".encodeToByteArray()),
                byteArrayOf()
            )

            val scanResponse = store.execute(
                CaseInsensitivePerson.scan(
                    where = MatchesPrefix("name" with "gar")
                )
            )

            assertIs<FetchByIndexScan>(scanResponse.dataFetchType)
            assertEquals(1, scanResponse.values.size)
            assertEquals(person, scanResponse.values.single().values)

            store.close()
        } finally {
            deleteFolder(folder)
        }
    }

    @Test
    fun malformedShortHistoricAdditionalMatchRowIsSkippedDuringHistoricScan() = runTest {
        val folder = createTestDBFolder("malformed-additional-match-historic-scan")

        try {
            val store = RocksDBDataStore.open(
                relativePath = folder,
                keepAllVersions = true,
                dataModelsById = mapOf(1u to CaseInsensitivePerson),
            )

            val person = CaseInsensitivePerson.create {
                firstName with "Jose"
                surname with "Garcia"
            }

            val addStatus = assertIs<AddSuccess<CaseInsensitivePerson>>(store.execute(CaseInsensitivePerson.add(person)).statuses.single())

            val index = CaseInsensitivePerson.Meta.indexes!![1]
            val columnFamilies = store.getColumnFamilies(CaseInsensitivePerson) as HistoricTableColumnFamilies
            val version = addStatus.version.toReversedVersionBytes()
            val malformedHistoricKey = createHistoricIndexKey(
                index.referenceStorageByteArray.bytes,
                "ga".encodeToByteArray(),
                version
            ).also {
                it.invert(it.size - version.size)
            }

            store.db.put(columnFamilies.historic.index, malformedHistoricKey, byteArrayOf())

            val scanResponse = store.execute(
                CaseInsensitivePerson.scan(
                    where = MatchesPrefix("name" with "gar"),
                    toVersion = addStatus.version
                )
            )

            assertIs<FetchByIndexScan>(scanResponse.dataFetchType)
            assertEquals(1, scanResponse.values.size)
            assertEquals(person, scanResponse.values.single().values)

            store.close()
        } finally {
            deleteFolder(folder)
        }
    }

    @Test
    fun malformedHistoricAdditionalMatchSiblingRowIsSkippedDuringHistoricScan() = runTest {
        val folder = createTestDBFolder("malformed-additional-match-historic-sibling-scan")

        try {
            val store = RocksDBDataStore.open(
                relativePath = folder,
                keepAllVersions = true,
                dataModelsById = mapOf(1u to CaseInsensitivePerson),
            )

            val person = CaseInsensitivePerson.create {
                firstName with "Jose"
                surname with "Garcia"
            }

            val addStatus = assertIs<AddSuccess<CaseInsensitivePerson>>(store.execute(CaseInsensitivePerson.add(person)).statuses.single())

            val index = CaseInsensitivePerson.Meta.indexes!![1]
            val columnFamilies = store.getColumnFamilies(CaseInsensitivePerson) as HistoricTableColumnFamilies
            val version = addStatus.version.toReversedVersionBytes()
            val malformedHistoricKey = createHistoricIndexKey(
                index.referenceStorageByteArray.bytes,
                "garz".encodeToByteArray(),
                version
            ).also {
                it.invert(it.size - version.size)
            }

            store.db.put(columnFamilies.historic.index, malformedHistoricKey, byteArrayOf())

            val scanResponse = store.execute(
                CaseInsensitivePerson.scan(
                    where = MatchesPrefix("name" with "gar"),
                    toVersion = addStatus.version
                )
            )

            assertIs<FetchByIndexScan>(scanResponse.dataFetchType)
            assertEquals(1, scanResponse.values.size)
            assertEquals(person, scanResponse.values.single().values)

            store.close()
        } finally {
            deleteFolder(folder)
        }
    }
}
