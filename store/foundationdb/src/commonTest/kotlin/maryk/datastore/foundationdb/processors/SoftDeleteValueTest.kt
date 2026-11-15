package maryk.datastore.foundationdb.processors

import maryk.core.clock.HLC
import maryk.core.exceptions.StorageException
import maryk.core.models.key
import maryk.core.query.changes.DataObjectVersionedChange
import maryk.core.query.changes.ObjectSoftDeleteChange
import maryk.datastore.foundationdb.FoundationDBDataStore
import maryk.datastore.foundationdb.HistoricTableDirectories
import maryk.datastore.foundationdb.runBoundedIntegrationTest
import maryk.datastore.foundationdb.processors.helpers.packKey
import maryk.datastore.foundationdb.processors.helpers.encodeZeroFreeSuffixUsing01
import maryk.datastore.foundationdb.processors.helpers.toReversedVersionBytes
import maryk.datastore.test.dataModelsForTests
import maryk.test.models.SimpleMarykModel
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertFailsWith
import kotlin.uuid.Uuid

class SoftDeleteValueTest {
    @Test
    fun repeatedHistoryReadsKeepVersionBoundsLegacyMarkersAndKeyIdentity() = runBoundedIntegrationTest {
        val store = FoundationDBDataStore.open(
            directoryPath = listOf("maryk", "soft-delete-history-preparation", Uuid.random().toString()),
            dataModelsById = dataModelsForTests, keepAllVersions = true,
        )
        try {
            val dirs = store.getTableDirs(SimpleMarykModel) as HistoricTableDirectories
            val keys = List(12) { index -> SimpleMarykModel.key(SimpleMarykModel.create { value with "ha key-$index" }) }
            val key = keys.first()
            val prefix = packKey(dirs.historicTablePrefix, encodeZeroFreeSuffixUsing01(key.bytes + SOFT_DELETE_INDICATOR, key.size))
            store.runTransaction { tr ->
                tr.set(packKey(prefix, byteArrayOf(0), 5uL.toReversedVersionBytes()), byteArrayOf(TRUE))
                tr.set(packKey(prefix, byteArrayOf(0), 7uL.toReversedVersionBytes()), byteArrayOf(0))
                tr.set(packKey(dirs.tablePrefix, key.bytes + SOFT_DELETE_INDICATOR), HLC.toStorageBytes(HLC(9uL)) + TRUE)
                for ((index, other) in keys.withIndex().drop(1)) {
                    tr.set(packKey(dirs.tablePrefix, other.bytes + SOFT_DELETE_INDICATOR),
                        HLC.toStorageBytes(HLC(100uL + index.toULong())) + if (index % 2 == 0) TRUE else 0.toByte())
                }
            }
            store.runTransaction { tr ->
                val cache = HistoryReadMetadataCache(dirs)
                fun read(index: Int, from: ULong, to: ULong, max: UInt = 1u) = addSoftDeleteChangeIfMissing(
                    tr, dirs, keys[index], from, null, toVersion = to, maxVersions = max, currentStateCache = cache,
                )?.changes.orEmpty().flatMap { version ->
                    version.changes.filterIsInstance<ObjectSoftDeleteChange>().map { version.version to it.isDeleted }
                }
                assertEquals(listOf(5uL to true), read(0, 5uL, 5uL))
                assertEquals(listOf(7uL to false), read(0, 7uL, 7uL))
                assertEquals(listOf(9uL to true), read(0, 9uL, 9uL))
                assertEquals(emptyList(), read(0, 6uL, 6uL))
                assertEquals(listOf(5uL to true), read(0, 0uL, 6uL))
                assertEquals(listOf(7uL to false), read(0, 6uL, 8uL))
                assertEquals(listOf(7uL to false, 9uL to true), read(0, 0uL, 10uL, 2u))
                for (index in 1..11) {
                    val version = 100uL + index.toULong()
                    assertEquals(listOf(version to (index % 2 == 0)), read(index, version, version))
                }
                assertEquals(listOf(9uL to true), read(0, 9uL, 9uL))
                assertEquals(emptyList(), read(0, 10uL, 8uL))
            }
        } finally { store.close() }
    }

    @Test
    fun exactAndRangeHistoryReadsRejectMalformedDeletionPayloads() = runBoundedIntegrationTest {
        val store = FoundationDBDataStore.open(
            directoryPath = listOf("maryk", "soft-delete-history-malformed", Uuid.random().toString()),
            dataModelsById = dataModelsForTests, keepAllVersions = true,
        )
        try {
            val dirs = store.getTableDirs(SimpleMarykModel) as HistoricTableDirectories
            val key = SimpleMarykModel.key(SimpleMarykModel.create { value with "ha malformed" })
            val prefix = packKey(dirs.historicTablePrefix, encodeZeroFreeSuffixUsing01(key.bytes + SOFT_DELETE_INDICATOR, key.size))
            store.runTransaction { tr -> tr.set(packKey(prefix, byteArrayOf(0), 11uL.toReversedVersionBytes()), byteArrayOf(TRUE, 0)) }
            for (from in listOf(0uL, 11uL)) {
                assertFailsWith<StorageException> {
                    store.runTransaction { tr -> addSoftDeleteChangeIfMissing(tr, dirs, key, from, null, toVersion = 11uL, maxVersions = 1u) }
                }
            }
        } finally { store.close() }
    }

    @Test
    fun currentIsSoftDeletedIgnoresOverlongValue() {
        runBoundedIntegrationTest {
            val store = FoundationDBDataStore.open(
                directoryPath = listOf("maryk", "test", "soft-delete-current-overlong", Uuid.random().toString()),
                dataModelsById = dataModelsForTests,
                keepAllVersions = true,
            )

            try {
                val tableDirs = store.getTableDirs(SimpleMarykModel)
                val key = SimpleMarykModel.key(
                    SimpleMarykModel.create {
                        value with "haha"
                    }
                )
                store.runTransaction { tr ->
                    tr.set(
                        packKey(tableDirs.tablePrefix, key.bytes + SOFT_DELETE_INDICATOR),
                        HLC.toStorageBytes(HLC(5uL)) + byteArrayOf(TRUE, 0)
                    )
                }

                assertFalse(store.runTransaction { tr ->
                    isSoftDeleted(tr, tableDirs, null, key.bytes)
                })
            } finally {
                store.close()
            }
        }
    }

    @Test
    fun softDeleteFallbackIgnoresValueWithoutDeleteFlag() {
        runBoundedIntegrationTest {
            val store = FoundationDBDataStore.open(
                directoryPath = listOf("maryk", "test", "soft-delete-fallback-missing-flag", Uuid.random().toString()),
                dataModelsById = dataModelsForTests,
                keepAllVersions = true,
            )

            try {
                val tableDirs = store.getTableDirs(SimpleMarykModel)
                val key = SimpleMarykModel.key(
                    SimpleMarykModel.create {
                        value with "haha"
                    }
                )
                val version = 5uL
                store.runTransaction { tr ->
                    tr.set(
                        packKey(tableDirs.tablePrefix, key.bytes + SOFT_DELETE_INDICATOR),
                        HLC.toStorageBytes(HLC(version))
                    )
                }

                val objectChange = DataObjectVersionedChange(key, changes = emptyList())
                assertEquals(
                    objectChange,
                    store.runTransaction { tr ->
                        addSoftDeleteChangeIfMissing(
                            tr = tr,
                            tableDirs = tableDirs,
                            key = key,
                            fromVersion = version,
                            objectChange = objectChange,
                        )
                    }
                )
            } finally {
                store.close()
            }
        }
    }
}
