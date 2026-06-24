package maryk.datastore.rocksdb.processors.helpers

import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDateTime
import maryk.core.query.pairs.with
import maryk.core.query.requests.add
import maryk.core.query.responses.statuses.AddSuccess
import maryk.createTestDBFolder
import maryk.datastore.rocksdb.DBAccessor
import maryk.datastore.rocksdb.HistoricTableColumnFamilies
import maryk.datastore.rocksdb.RocksDBDataStore
import maryk.deleteFolder
import maryk.test.models.Option
import maryk.test.models.TestMarykModel
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class HistoricQualifierRetrieverTest {
    @Test
    fun historicQualifierRetrieverSkipsShortRowAndYieldsNextQualifier() = runTest {
        val folder = createTestDBFolder("historic-qualifier-retriever-short-row")

        try {
            val store = RocksDBDataStore.open(
                relativePath = folder,
                keepAllVersions = true,
                dataModelsById = mapOf(1u to TestMarykModel),
            )

            val addStatus = assertIs<AddSuccess<TestMarykModel>>(
                store.execute(
                    TestMarykModel.add(
                        TestMarykModel.create {
                            int with 5
                            uint with 1u
                            double with 1.2
                            dateTime with LocalDateTime(2024, 1, 1, 0, 0)
                            bool with true
                            enum with Option.V1
                        }
                    )
                ).statuses.single()
            )

            val columnFamilies = store.getColumnFamilies(TestMarykModel) as HistoricTableColumnFamilies
            val validQualifier = byteArrayOf(0)
            val versionBytes = addStatus.version.toReversedVersionBytes()

            store.db.put(
                columnFamilies.historic.table,
                addStatus.key.bytes + validQualifier + byteArrayOf(versionBytes.first()),
                byteArrayOf(99)
            )
            store.db.put(
                columnFamilies.historic.table,
                addStatus.key.bytes + validQualifier + versionBytes,
                byteArrayOf(42)
            )

            DBAccessor(store).use { accessor ->
                accessor.getIterator(store.defaultReadOptions, columnFamilies.historic.table).use { iterator ->
                    iterator.seek(addStatus.key.bytes)

                    val seenVersions = mutableListOf<ULong>()
                    val nextQualifier = iterator.historicQualifierRetriever(
                        addStatus.key,
                        addStatus.version,
                        1u
                    ) { version ->
                        seenVersions += version
                    }

                    val qualifiers = mutableListOf<ByteArray>()
                    while (nextQualifier { reader, length ->
                        qualifiers += ByteArray(length) { reader(it) }
                    }) {
                    }

                    assertTrue(qualifiers.isNotEmpty())
                    assertEquals(1, qualifiers.count { it.contentEquals(validQualifier) })
                    assertTrue(seenVersions.contains(addStatus.version))
                    assertContentEquals(validQualifier, qualifiers.first { it.contentEquals(validQualifier) })
                }
            }

            store.close()
        } finally {
            deleteFolder(folder)
        }
    }
}
