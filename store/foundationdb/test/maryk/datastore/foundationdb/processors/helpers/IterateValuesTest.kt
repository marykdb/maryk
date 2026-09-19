package maryk.datastore.foundationdb.processors.helpers

import kotlinx.coroutines.test.runTest
import maryk.core.clock.HLC
import maryk.datastore.foundationdb.FoundationDBDataStore
import maryk.datastore.foundationdb.HistoricTableDirectories
import maryk.datastore.test.dataModelsForTests
import maryk.test.models.CompleteMarykModel
import kotlin.test.Test
import kotlin.test.assertNull
import kotlin.uuid.Uuid

class IterateValuesTest {
    @Test
    fun currentIterateValuesSkipsReferenceShorterThanKeyLength() = runTest {
        val store = FoundationDBDataStore.open(
            directoryPath = listOf("maryk", "test", "iterate-values-short-reference", Uuid.random().toString()),
            dataModelsById = dataModelsForTests,
            keepAllVersions = true,
        )

        try {
            val tableDirs = store.getTableDirs(CompleteMarykModel)
            val shortReference = byteArrayOf(1)

            store.runTransaction { tr ->
                tr.set(
                    packKey(tableDirs.tablePrefix, shortReference),
                    HLC.toStorageBytes(HLC(1uL)) + byteArrayOf(7)
                )
            }

            val result = store.runTransaction { tr ->
                tr.iterateValues(
                    tableDirs,
                    toVersion = null,
                    keyLength = CompleteMarykModel.Meta.keyByteSize,
                    reference = shortReference,
                ) { _, _, _, _, _, _ ->
                    error("short reference should be skipped")
                }
            }

            assertNull(result)
        } finally {
            store.close()
        }
    }

    @Test
    fun historicIterateValuesSkipsMalformedEncodedQualifier() = runTest {
        val store = FoundationDBDataStore.open(
            directoryPath = listOf("maryk", "test", "iterate-values-malformed-qualifier", Uuid.random().toString()),
            dataModelsById = dataModelsForTests,
            keepAllVersions = true,
        )

        try {
            val tableDirs = store.getTableDirs(CompleteMarykModel) as HistoricTableDirectories
            val key = ByteArray(CompleteMarykModel.Meta.keyByteSize) { (it + 1).toByte() }
            val reference = byteArrayOf(5)
            val invalidEncodedQualifier = byteArrayOf(5, 1, 0)
            val version = 10uL

            store.runTransaction { tr ->
                tr.set(
                    packVersionedKey(
                        tableDirs.historicTablePrefix,
                        key,
                        invalidEncodedQualifier,
                        version = HLC.toStorageBytes(HLC(version))
                    ),
                    byteArrayOf(7)
                )
            }

            val result = store.runTransaction { tr ->
                tr.iterateValues(
                    tableDirs,
                    toVersion = version,
                    keyLength = key.size,
                    reference = key + reference,
                ) { value, offset, length, _, _, _ ->
                    value.copyOfRange(offset, offset + length)
                }
            }

            assertNull(result)
        } finally {
            store.close()
        }
    }

    @Test
    fun historicIterateValuesSkipsMalformedVersionSeparator() = runTest {
        val store = FoundationDBDataStore.open(
            directoryPath = listOf("maryk", "test", "iterate-values-malformed-separator", Uuid.random().toString()),
            dataModelsById = dataModelsForTests,
            keepAllVersions = true,
        )

        try {
            val tableDirs = store.getTableDirs(CompleteMarykModel) as HistoricTableDirectories
            val key = ByteArray(CompleteMarykModel.Meta.keyByteSize) { (it + 1).toByte() }
            val reference = byteArrayOf(5)
            val version = 10uL
            val malformedKey = packKey(
                tableDirs.historicTablePrefix,
                key,
                reference,
                byteArrayOf(1),
                version.toReversedVersionBytes()
            )

            store.runTransaction { tr ->
                tr.set(malformedKey, byteArrayOf(7))
            }

            val result = store.runTransaction { tr ->
                tr.iterateValues(
                    tableDirs,
                    toVersion = version,
                    keyLength = key.size,
                    reference = key + reference,
                ) { value, offset, length, _, _, _ ->
                    value.copyOfRange(offset, offset + length)
                }
            }

            assertNull(result)
        } finally {
            store.close()
        }
    }
}
