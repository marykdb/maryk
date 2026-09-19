package maryk.datastore.foundationdb

import kotlinx.coroutines.test.runTest
import maryk.core.query.filters.MatchesPrefix
import maryk.core.query.pairs.with
import maryk.core.query.requests.add
import maryk.core.query.requests.scan
import maryk.core.query.responses.FetchByIndexScan
import maryk.core.query.responses.statuses.AddSuccess
import maryk.datastore.foundationdb.processors.helpers.encodeZeroFreeUsing01
import maryk.datastore.foundationdb.processors.helpers.packKey
import maryk.datastore.foundationdb.processors.helpers.packVersionedKey
import maryk.datastore.foundationdb.processors.helpers.toReversedVersionBytes
import maryk.test.models.CaseInsensitivePerson
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.time.Duration.Companion.minutes
import kotlin.uuid.Uuid

class MalformedAdditionalMatchFoundationDBTest {
    @Test
    fun malformedShortAdditionalMatchRowIsSkippedDuringScan() = runTest(timeout = 3.minutes) {
        val store = FoundationDBDataStore.open(
            fdbClusterFilePath = "./fdb.cluster",
            directoryPath = listOf("maryk", "test", "malformed-additional-match-scan", Uuid.random().toString()),
            dataModelsById = mapOf(1u to CaseInsensitivePerson),
            keepAllVersions = true,
        )

        try {
            val person = CaseInsensitivePerson.create {
                firstName with "Jose"
                surname with "Garcia"
            }

            assertIs<AddSuccess<CaseInsensitivePerson>>(store.execute(CaseInsensitivePerson.add(person)).statuses.single())

            val index = CaseInsensitivePerson.Meta.indexes!![1]
            val tableDirs = store.getTableDirs(CaseInsensitivePerson)

            store.runTransaction { tr ->
                tr.set(
                    packKey(tableDirs.indexPrefix, index.referenceStorageByteArray.bytes, "ga".encodeToByteArray()),
                    byteArrayOf()
                )
            }

            val scanResponse = store.execute(
                CaseInsensitivePerson.scan(
                    where = MatchesPrefix("name" with "gar")
                )
            )

            assertIs<FetchByIndexScan>(scanResponse.dataFetchType)
            assertEquals(1, scanResponse.values.size)
            assertEquals(person, scanResponse.values.single().values)
        } finally {
            store.close()
        }
    }

    @Test
    fun malformedShortHistoricAdditionalMatchRowIsSkippedDuringHistoricScan() = runTest(timeout = 3.minutes) {
        val store = FoundationDBDataStore.open(
            fdbClusterFilePath = "./fdb.cluster",
            directoryPath = listOf("maryk", "test", "malformed-additional-match-historic-scan", Uuid.random().toString()),
            dataModelsById = mapOf(1u to CaseInsensitivePerson),
            keepAllVersions = true,
        )

        try {
            val person = CaseInsensitivePerson.create {
                firstName with "Jose"
                surname with "Garcia"
            }

            val addStatus = assertIs<AddSuccess<CaseInsensitivePerson>>(store.execute(CaseInsensitivePerson.add(person)).statuses.single())

            val index = CaseInsensitivePerson.Meta.indexes!![1]
            val tableDirs = store.getTableDirs(CaseInsensitivePerson) as HistoricTableDirectories
            val malformedQualifier = encodeZeroFreeUsing01(index.referenceStorageByteArray.bytes + "ga".encodeToByteArray())

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
                CaseInsensitivePerson.scan(
                    where = MatchesPrefix("name" with "gar"),
                    toVersion = addStatus.version
                )
            )

            assertIs<FetchByIndexScan>(scanResponse.dataFetchType)
            assertEquals(1, scanResponse.values.size)
            assertEquals(person, scanResponse.values.single().values)
        } finally {
            store.close()
        }
    }
}
