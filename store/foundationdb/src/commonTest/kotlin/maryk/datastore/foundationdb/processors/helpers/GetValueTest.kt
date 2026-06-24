package maryk.datastore.foundationdb.processors.helpers

import kotlinx.coroutines.test.runTest
import maryk.core.clock.HLC
import maryk.core.exceptions.StorageException
import maryk.datastore.foundationdb.FoundationDBDataStore
import maryk.datastore.foundationdb.HistoricTableDirectories
import maryk.datastore.test.dataModelsForTests
import maryk.test.models.CompleteMarykModel
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.uuid.Uuid

class GetValueTest {
    @Test
    fun currentGetValueRejectsMissingVersionPrefix() = runTest {
        val store = FoundationDBDataStore.open(
            directoryPath = listOf("maryk", "test", "get-value-missing-version", Uuid.random().toString()),
            dataModelsById = dataModelsForTests,
            keepAllVersions = true,
        )

        try {
            val tableDirs = store.getTableDirs(CompleteMarykModel)
            val keyAndReference = ByteArray(CompleteMarykModel.Meta.keyByteSize) { (it + 1).toByte() } + byteArrayOf(5)

            store.runTransaction { tr ->
                tr.set(packKey(tableDirs.tablePrefix, keyAndReference), byteArrayOf(1))
            }

            assertFailsWith<StorageException> {
                store.runTransaction { tr ->
                    tr.getValue(
                        tableDirs = tableDirs,
                        toVersion = null,
                        keyAndReference = keyAndReference,
                    ) { value, offset, length ->
                        value.copyOfRange(offset, offset + length)
                    }
                }
            }
        } finally {
            store.close()
        }
    }

    @Test
    fun historicGetValueIgnoresPrefixCollidingQualifier() = runTest {
        val store = FoundationDBDataStore.open(
            directoryPath = listOf("maryk", "test", "get-value-prefix-collision", Uuid.random().toString()),
            dataModelsById = dataModelsForTests,
            keepAllVersions = true,
        )

        try {
            val tableDirs = store.getTableDirs(CompleteMarykModel) as HistoricTableDirectories
            val key = ByteArray(CompleteMarykModel.Meta.keyByteSize) { (it + 1).toByte() }
            val reference = byteArrayOf(5)
            val collidingReference = byteArrayOf(5, 6)
            val version = 10uL
            val versionBytes = HLC.toStorageBytes(HLC(version))
            val collidingPayload = byteArrayOf(42)
            val exactPayload = byteArrayOf(7)

            store.runTransaction { tr ->
                tr.set(
                    packVersionedKey(
                        tableDirs.historicTablePrefix,
                        key,
                        encodeZeroFreeUsing01(collidingReference),
                        version = versionBytes
                    ),
                    collidingPayload
                )
            }

            val missing = store.runTransaction { tr ->
                tr.getValue(
                    tableDirs = tableDirs,
                    toVersion = version,
                    keyAndReference = key + reference,
                    keyLength = key.size,
                ) { value, offset, length ->
                    value.copyOfRange(offset, offset + length)
                }
            }
            assertNull(missing)

            store.runTransaction { tr ->
                tr.set(
                    packVersionedKey(
                        tableDirs.historicTablePrefix,
                        key,
                        encodeZeroFreeUsing01(reference),
                        version = versionBytes
                    ),
                    exactPayload
                )
            }

            val exact = store.runTransaction { tr ->
                tr.getValue(
                    tableDirs = tableDirs,
                    toVersion = version,
                    keyAndReference = key + reference,
                    keyLength = key.size,
                ) { value, offset, length ->
                    value.copyOfRange(offset, offset + length)
                }
            }
            assertContentEquals(exactPayload, exact)
        } finally {
            store.close()
        }
    }
}
