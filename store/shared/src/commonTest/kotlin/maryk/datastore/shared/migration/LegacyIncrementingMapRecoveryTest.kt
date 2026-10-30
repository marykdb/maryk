package maryk.datastore.shared.migration

import kotlinx.coroutines.test.runTest
import maryk.core.models.RootDataModel
import maryk.core.models.migration.MigrationException
import maryk.core.properties.definitions.StringDefinition
import maryk.core.properties.definitions.incrementingMap
import maryk.core.properties.types.numeric.UInt32
import maryk.datastore.shared.decodeIncrementingMapMaximum
import maryk.datastore.shared.incrementingMapKeysForQualifier
import maryk.datastore.shared.incrementingMapWatermarkPrefix
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertIs
import kotlin.test.assertTrue

private object AllocationRecoveryRecord : RootDataModel<AllocationRecoveryRecord>() {
    val entries by incrementingMap(1u, keyNumberDescriptor = UInt32, valueDefinition = StringDefinition())
}

class LegacyIncrementingMapRecoveryTest {
    private class Interrupted : RuntimeException()

    @Test
    fun rejectsEditedRecordWithoutHistoryEvenWhenCurrentMaximumWasObserved() = runTest {
        val metadata = mutableMapOf<String, ByteArray>()
        val recovery = LegacyIncrementingMapRecovery(1u, AllocationRecoveryRecord,
            read = { metadata[it.toHexString()] }, write = { key, value -> metadata[key.toHexString()] = value })
        val record = ByteArray(16)
        recovery.observe(record, AllocationRecoveryRecord.ref { entries at 3u }.toStorageByteArray())
        val failure = assertFailsWith<MigrationException> {
            recovery.verify(record, ByteArray(8), ByteArray(8) { 1 }, verifyContinuousHistory = { false })
        }
        assertTrue(failure.message.orEmpty().contains("current keys cannot prove erased allocations"))
        assertNull(metadata[legacyIncrementingMapProofKey(1u, record).toHexString()])
    }

    @Test
    fun creationOnlyRecordNeedsNoHistoryAndInterruptedRecoveryNeverLowersMaximum() = runTest {
        val metadata = mutableMapOf<String, ByteArray>()
        val recovery = LegacyIncrementingMapRecovery(1u, AllocationRecoveryRecord,
            read = { metadata[it.toHexString()] }, write = { key, value -> metadata[key.toHexString()] = value })
        val record = ByteArray(16)
        val creationOnly = ByteArray(16) { 1 }
        recovery.verify(creationOnly, ByteArray(8), ByteArray(8), verifyContinuousHistory = { error("No history is needed") })
        val highest = AllocationRecoveryRecord.ref { entries at 10u }.toStorageByteArray()
        assertFailsWith<Interrupted> {
            recovery.verify(record, ByteArray(8), ByteArray(8) { 1 }, verifyContinuousHistory = {
                recovery.observe(record, highest)
                throw Interrupted()
            })
        }
        assertNull(metadata[legacyIncrementingMapProofKey(1u, record).toHexString()])
        recovery.verify(record, ByteArray(8), ByteArray(8) { 1 }, verifyContinuousHistory = {
            recovery.observe(record, AllocationRecoveryRecord.ref { entries at 7u }.toStorageByteArray())
            true
        })
        val reference = incrementingMapKeysForQualifier(AllocationRecoveryRecord, highest).single().reference
        val stored = metadata.getValue((incrementingMapWatermarkPrefix(1u, record) + reference.toStorageByteArray()).toHexString())
        assertEquals(10u, assertIs<UInt>(decodeIncrementingMapMaximum(reference, stored)))
    }
}
