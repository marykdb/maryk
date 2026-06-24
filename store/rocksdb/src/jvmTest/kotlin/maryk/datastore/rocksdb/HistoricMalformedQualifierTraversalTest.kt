package maryk.datastore.rocksdb

import kotlinx.coroutines.test.runTest
import maryk.core.clock.HLC
import maryk.core.extensions.bytes.invert
import maryk.core.models.key
import maryk.core.query.changes.ObjectCreate
import maryk.core.query.pairs.with
import maryk.core.query.requests.add
import maryk.core.query.requests.get
import maryk.core.query.requests.getChanges
import maryk.core.query.requests.scanChanges
import maryk.core.query.responses.statuses.AddSuccess
import maryk.createTestDBFolder
import maryk.datastore.rocksdb.processors.helpers.toReversedVersionBytes
import maryk.datastore.shared.TypeIndicator
import maryk.datastore.test.NullableUniqueModel
import maryk.datastore.test.dataModelsForTests
import maryk.deleteFolder
import maryk.lib.bytes.combineToByteArray
import maryk.test.models.Log
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class HistoricMalformedQualifierTraversalTest {
    @Test
    fun historicGetSkipsMalformedShortQualifierRow() = runTest {
        val folder = createTestDBFolder("historic-get-malformed-qualifier")

        try {
            val dataStore = RocksDBDataStore.open(
                relativePath = folder,
                keepAllVersions = true,
                dataModelsById = dataModelsForTests,
            )

            val values = Log("historic-get-malformed")
            val key = Log.key(values)
            val addStatus = assertIs<AddSuccess<Log>>(
                dataStore.execute(Log.add(key to values)).statuses.first()
            )

            val columnFamilies = dataStore.getColumnFamilies(Log) as HistoricTableColumnFamilies
            val versionBytes = addStatus.version.toReversedVersionBytes()
            dataStore.db.put(
                columnFamilies.historic.table,
                key.bytes + byteArrayOf(0, versionBytes.first()),
                byteArrayOf(99)
            )

            val response = dataStore.execute(
                Log.get(
                    key,
                    toVersion = addStatus.version,
                    filterSoftDeleted = false,
                )
            )

            assertEquals(1, response.values.size)
            assertEquals(key, response.values.single().key)
            assertEquals(values { message }, response.values.single().values { message })
            assertEquals(values { severity }, response.values.single().values { severity })

            dataStore.close()
        } finally {
            deleteFolder(folder)
        }
    }

    @Test
    fun historicScanChangesSkipsMalformedShortQualifierRow() = runTest {
        val folder = createTestDBFolder("historic-scan-changes-malformed-qualifier")

        try {
            val dataStore = RocksDBDataStore.open(
                relativePath = folder,
                keepAllVersions = true,
                dataModelsById = dataModelsForTests,
            )

            val values = Log("historic-scan-changes-malformed")
            val key = Log.key(values)
            val addStatus = assertIs<AddSuccess<Log>>(
                dataStore.execute(Log.add(key to values)).statuses.first()
            )

            val columnFamilies = dataStore.getColumnFamilies(Log) as HistoricTableColumnFamilies
            val versionBytes = addStatus.version.toReversedVersionBytes()
            dataStore.db.put(
                columnFamilies.historic.table,
                key.bytes + byteArrayOf(0, versionBytes.first()),
                byteArrayOf(99)
            )

            val response = dataStore.execute(
                Log.scanChanges(
                    startKey = key,
                    includeStart = true,
                    limit = 1u,
                    toVersion = addStatus.version,
                    maxVersions = 10u,
                    filterSoftDeleted = false,
                )
            )

            assertEquals(1, response.changes.size)
            assertEquals(key, response.changes.single().key)
            assertTrue(response.changes.single().changes.any { versioned ->
                versioned.version == addStatus.version && versioned.changes.contains(ObjectCreate)
            })

            dataStore.close()
        } finally {
            deleteFolder(folder)
        }
    }

    @Test
    fun historicGetSkipsDeleteMarkerValuePayload() = runTest {
        val folder = createTestDBFolder("historic-get-delete-marker-value")

        try {
            val dataStore = RocksDBDataStore.open(
                relativePath = folder,
                keepAllVersions = true,
                dataModelsById = mapOf(1u to NullableUniqueModel),
            )

            val addStatus = assertIs<AddSuccess<NullableUniqueModel>>(
                dataStore.execute(
                    NullableUniqueModel.add(
                        NullableUniqueModel.create {
                            email with "historic-delete-marker@test.com"
                        }
                    )
                ).statuses.single()
            )

            dataStore.putHistoricDeleteMarkerForEmail(addStatus)

            val response = dataStore.execute(
                NullableUniqueModel.get(
                    addStatus.key,
                    toVersion = addStatus.version,
                    filterSoftDeleted = false,
                )
            )

            assertEquals(0, response.values.size)

            dataStore.close()
        } finally {
            deleteFolder(folder)
        }
    }

    @Test
    fun historicGetChangesSkipsDeleteMarkerValuePayload() = runTest {
        val folder = createTestDBFolder("historic-get-changes-delete-marker-value")

        try {
            val dataStore = RocksDBDataStore.open(
                relativePath = folder,
                keepAllVersions = true,
                dataModelsById = mapOf(1u to NullableUniqueModel),
            )

            val addStatus = assertIs<AddSuccess<NullableUniqueModel>>(
                dataStore.execute(
                    NullableUniqueModel.add(
                        NullableUniqueModel.create {
                            email with "historic-delete-marker-changes@test.com"
                        }
                    )
                ).statuses.single()
            )

            dataStore.putHistoricDeleteMarkerForEmail(addStatus)

            val response = dataStore.execute(
                NullableUniqueModel.getChanges(
                    addStatus.key,
                    toVersion = addStatus.version,
                    maxVersions = 10u,
                    filterSoftDeleted = false,
                )
            )

            assertEquals(1, response.changes.size)
            assertEquals(addStatus.key, response.changes.single().key)

            dataStore.close()
        } finally {
            deleteFolder(folder)
        }
    }

    @Test
    fun historicGetChangesHandlesEmptyObjectDeletePayload() = runTest {
        val folder = createTestDBFolder("historic-get-changes-empty-object-delete")

        try {
            val dataStore = RocksDBDataStore.open(
                relativePath = folder,
                keepAllVersions = true,
                dataModelsById = dataModelsForTests,
            )

            val values = Log("historic-empty-object-delete")
            val key = Log.key(values)
            val addStatus = assertIs<AddSuccess<Log>>(
                dataStore.execute(Log.add(key to values)).statuses.first()
            )

            dataStore.putEmptyHistoricObjectDeletePayload(key.bytes, addStatus.version)

            val response = dataStore.execute(
                Log.getChanges(
                    key,
                    toVersion = addStatus.version,
                    maxVersions = 10u,
                    filterSoftDeleted = false,
                )
            )

            assertEquals(1, response.changes.size)
            assertEquals(key, response.changes.single().key)

            dataStore.close()
        } finally {
            deleteFolder(folder)
        }
    }

    private fun RocksDBDataStore.putHistoricDeleteMarkerForEmail(
        addStatus: AddSuccess<NullableUniqueModel>
    ) {
        val columnFamilies = getColumnFamilies(NullableUniqueModel) as HistoricTableColumnFamilies
        val versionBytes = HLC.toStorageBytes(HLC(addStatus.version))
        val reference = NullableUniqueModel { email::ref }.toStorageByteArray()
        val historicReference = combineToByteArray(addStatus.key.bytes, reference, versionBytes).apply {
            invert(size - versionBytes.size)
        }

        db.put(
            columnFamilies.historic.table,
            historicReference,
            TypeIndicator.DeletedIndicator.byteArray
        )
    }

    private fun RocksDBDataStore.putEmptyHistoricObjectDeletePayload(
        keyBytes: ByteArray,
        version: ULong
    ) {
        val columnFamilies = getColumnFamilies(Log) as HistoricTableColumnFamilies
        val versionBytes = HLC.toStorageBytes(HLC(version))
        val historicReference = combineToByteArray(keyBytes, byteArrayOf(0), versionBytes).apply {
            invert(size - versionBytes.size)
        }

        db.put(columnFamilies.historic.table, historicReference, byteArrayOf())
    }
}
