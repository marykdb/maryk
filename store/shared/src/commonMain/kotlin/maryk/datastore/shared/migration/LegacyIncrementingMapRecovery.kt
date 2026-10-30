package maryk.datastore.shared.migration

import maryk.core.extensions.bytes.toVarBytes
import maryk.core.models.IsRootDataModel
import maryk.core.models.migration.MigrationException
import maryk.core.models.migration.convertLegacyStorageQualifier
import maryk.datastore.shared.decodeIncrementingMapMaximum
import maryk.datastore.shared.encodeIncrementingMapMaximum
import maryk.datastore.shared.incrementingMapKeysForQualifier
import maryk.datastore.shared.incrementingMapWatermarkPrefix

/** Recovery attests the complete record lifetime, including removed map/parent instances. */
fun legacyIncrementingMapProofKey(modelId: UInt, record: ByteArray): ByteArray =
    "maryk.incmap.verified/$modelId/".encodeToByteArray() + record.size.toVarBytes() + record

/** Maxima and proof commit before Backfill; retries only increase maxima. */
class LegacyIncrementingMapRecovery(
    private val modelId: UInt,
    private val model: IsRootDataModel,
    private val read: suspend (ByteArray) -> ByteArray?,
    private val write: suspend (ByteArray, ByteArray) -> Unit,
) {
    private val required = model.hasLegacyIncrementingMapStorage()

    suspend fun observe(record: ByteArray, qualifier: ByteArray) {
        if (!required || qualifier.isEmpty()) return
        val current = model.convertLegacyStorageQualifier(qualifier).bytes
        for (maximum in incrementingMapKeysForQualifier(model, current)) {
            val key = incrementingMapWatermarkPrefix(modelId, record) + maximum.reference.toStorageByteArray()
            val old = read(key)?.let { decodeIncrementingMapMaximum(maximum.reference, it) }
            if (old == null || maximum.key > old) write(key, encodeIncrementingMapMaximum(maximum))
        }
    }

    suspend fun verify(
        record: ByteArray,
        creation: ByteArray?,
        latest: ByteArray?,
        verifyContinuousHistory: suspend () -> Boolean,
    ) {
        if (!required) return
        if (creation?.size != 8 || latest?.size != 8 || creation.toHexString() > latest.toHexString()) {
            throw MigrationException("Malformed allocation lifetime versions: model=$modelId record=${record.toHexString()} " +
                "creation=${creation?.toHexString()} latest=${latest?.toHexString()}; source rows are unchanged")
        }
        val proof = legacyIncrementingMapProofKey(modelId, record)
        read(proof)?.let { persisted ->
            if (!persisted.contentEquals(byteArrayOf(1))) {
                throw MigrationException("Malformed allocation recovery proof: model=$modelId record=${record.toHexString()}; source rows are unchanged")
            }
            return
        }
        if (!creation.contentEquals(latest) && !verifyContinuousHistory()) {
            throw MigrationException("Cannot prove the released incrementing-map lifetime maximum: model=$modelId " +
                "record=${record.toHexString()} creation=${creation.toHexString()} latest=${latest.toHexString()}. " +
                "Source rows are unchanged. Restore authoritative allocation maxima for every retained or removed map instance " +
                "from a continuous history/application log or backup and verify the record before reopening; current keys cannot prove erased allocations.")
        }
        write(proof, byteArrayOf(1))
    }
}
