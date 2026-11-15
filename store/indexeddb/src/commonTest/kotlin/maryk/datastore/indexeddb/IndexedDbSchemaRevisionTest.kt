package maryk.datastore.indexeddb

import kotlinx.coroutines.test.runTest
import maryk.core.exceptions.RequestException
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull

class IndexedDbSchemaRevisionTest {
    @Test
    fun schemaRevisionsTrackChangesWithoutInvalidatingIdenticalOpens() = runTest {
        installIndexedDbForTests()
        val native = openIndexedDbByteStore("schema-revision-${Random.nextInt()}", setOf("meta", "rows"))
        try {
            native.put("meta", OptionsMetadataKey, byteArrayOf(1))
            val original = assertNotNull(native.get("meta", SchemaRevisionMetadataKey))
            val baseline = native.captureSchemaSnapshot(emptySet())
            assertEquals(true, baseline.matches(native))
            native.put("meta", OptionsMetadataKey, byteArrayOf(1))
            native.put("rows", byteArrayOf(1), byteArrayOf(2))
            assertContentEquals(original, native.get("meta", SchemaRevisionMetadataKey))
            assertEquals(true, baseline.matches(native))

            // Another model changes the global stamp, but does not stale this baseline.
            native.put("meta", byteArrayOf(3, 0, 0, 0, 99), byteArrayOf(7))
            assertEquals(false, original.contentEquals(native.get("meta", SchemaRevisionMetadataKey)))
            assertEquals(true, baseline.matches(native))
            native.put("meta", OptionsMetadataKey, byteArrayOf(2))
            assertEquals(false, baseline.matches(native))
            native.put("meta", OptionsMetadataKey, byteArrayOf(1))
            assertEquals(true, baseline.matches(native))
        } finally { native.close() }
    }

    @Test
    fun nativeGuardedReadsKeepRecordOrderMissingKeysAndOwnership() = runTest {
        installIndexedDbForTests()
        val native = openIndexedDbByteStore("schema-records-${Random.nextInt()}", setOf("meta", "rows"))
        try {
            val baseline = native.captureSchemaSnapshot(emptySet())
            native.put("rows", byteArrayOf(1), byteArrayOf(11))
            native.put("rows", byteArrayOf(2), byteArrayOf(22))
            val reader = native as IndexedDbSchemaReadStore
            val keys = listOf(byteArrayOf(2), byteArrayOf(3), byteArrayOf(1), byteArrayOf(2))
            val rows = reader.getRecordsWithSchemaRevision("rows", keys)
            assertEquals(5, rows.size)
            assertEquals(true, baseline.matches(native, rows[0]))
            assertContentEquals(byteArrayOf(22), rows[1])
            assertEquals(null, rows[2])
            assertContentEquals(byteArrayOf(11), rows[3])
            rows[1]!![0] = 99
            assertContentEquals(byteArrayOf(22), rows[4])
            assertContentEquals(byteArrayOf(22), native.get("rows", byteArrayOf(2)))
            native.put("meta", OptionsMetadataKey, byteArrayOf(7))
            val changed = reader.getRecordsWithSchemaRevision("rows", keys)
            assertEquals(false, baseline.matches(native, changed[0]))
        } finally { native.close() }
    }

    @Test
    fun restoredMetadataCannotValidateHeadsFromAnInterveningSchema() = runTest {
        installIndexedDbForTests()
        val native = openIndexedDbByteStore("schema-aba-${Random.nextInt()}", setOf("meta", "rows"))
        try {
            native.put("meta", OptionsMetadataKey, byteArrayOf(1))
            val baseline = native.captureSchemaSnapshot(emptySet())
            native.put("meta", OptionsMetadataKey, byteArrayOf(2))
            val intervening = (native as IndexedDbSchemaReadStore).getRecordsWithSchemaRevision("rows", emptyList())
            native.put("meta", OptionsMetadataKey, byteArrayOf(1))
            assertEquals(false, baseline.matches(native, intervening[0]))
            assertEquals(true, baseline.matches(native))
        } finally { native.close() }
    }

    @Test
    fun multiReadRejectsRestoredMetadataButAllowsItBetweenRequests() = runTest {
        installIndexedDbForTests()
        val native = openIndexedDbByteStore("schema-multi-read-aba-${Random.nextInt()}", setOf("meta", "rows"))
        try {
            native.put("meta", OptionsMetadataKey, byteArrayOf(1))
            val baseline = native.captureSchemaSnapshot(emptySet())
            assertEquals(true, baseline.matches(native))
            val startingRevision = assertNotNull(baseline.validatedReadRevision)
            native.put("rows", byteArrayOf(1), byteArrayOf(11))
            assertEquals(true, baseline.matchesReadRevision(native, startingRevision))

            native.put("meta", OptionsMetadataKey, byteArrayOf(2))
            native.put("meta", OptionsMetadataKey, byteArrayOf(1))
            assertEquals(false, baseline.matchesReadRevision(native, startingRevision))
            assertEquals(true, baseline.matches(native))
            assertEquals(false, baseline.matchesReadRevision(native, startingRevision))
            assertEquals(true, baseline.matchesReadRevision(native, baseline.validatedReadRevision))

            // An unrelated model may be accepted by the next request, but a
            // request already reading must conservatively reject the transition.
            val nextRevision = baseline.validatedReadRevision
            native.put("meta", byteArrayOf(3, 0, 0, 0, 99), byteArrayOf(7))
            assertEquals(false, baseline.matchesReadRevision(native, nextRevision))
            assertEquals(true, baseline.matches(native))
        } finally { native.close() }
    }

    @Test
    fun pendingSchemaTransitionsInvalidateTheNativeRevisionCache() = runTest {
        installIndexedDbForTests()
        val native = openIndexedDbByteStore("schema-pending-${Random.nextInt()}", setOf("meta"))
        try {
            val baseline = native.captureSchemaSnapshot(setOf(1u))
            assertEquals(true, baseline.matches(native))
            val pendingKey = migrationStateMetadataKey(1u)
            native.put("meta", pendingKey, byteArrayOf(1))
            assertEquals(false, baseline.matches(native))
            native.delete("meta", pendingKey)
            assertEquals(true, baseline.matches(native))
        } finally { native.close() }
    }


    @Test
    fun metadataChangesBetweenBatchesCannotBeCachedAsValidated() = runTest {
        installIndexedDbForTests()
        val native = openIndexedDbByteStore("schema-batch-race-${Random.nextInt()}", setOf("meta"))
        try {
            val baseline = native.captureSchemaSnapshot((1u..90u).toSet())
            native.put("meta", byteArrayOf(3, 0, 1, 0, 0), byteArrayOf(1))
            var batches = 0
            val interleaved = object : IndexedDbByteStore by native, IndexedDbBatchReadStore {
                override suspend fun getBatchValues(storeName: String, keys: List<ByteArray>): List<ByteArray?> {
                    if (++batches == 2) native.put("meta", OptionsMetadataKey, byteArrayOf(2))
                    return native.getBatch(storeName, keys)
                }
            }
            assertEquals(false, baseline.matches(interleaved))
            assertEquals(2, batches)
            assertEquals(false, baseline.matches(native))
        } finally { native.close() }
    }

    @Test
    fun capturingABaselineRejectsAConcurrentSchemaChange() = runTest {
        installIndexedDbForTests()
        val native = openIndexedDbByteStore("schema-capture-race-${Random.nextInt()}", setOf("meta"))
        try {
            native.put("meta", OptionsMetadataKey, byteArrayOf(1))
            var batches = 0
            val interleaved = object : IndexedDbByteStore by native, IndexedDbBatchReadStore, SchemaRevisionAwareIndexedDbStore {
                override suspend fun getBatchValues(storeName: String, keys: List<ByteArray>): List<ByteArray?> {
                    if (++batches == 2) native.put("meta", OptionsMetadataKey, byteArrayOf(2))
                    return native.getBatch(storeName, keys)
                }
            }
            assertFailsWith<RequestException> { interleaved.captureSchemaSnapshot((1u..90u).toSet()) }
            assertEquals(2, batches)
        } finally { native.close() }
    }
}
