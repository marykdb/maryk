package maryk.datastore.memory.processors

import maryk.core.models.IsRootDataModel
import maryk.core.properties.graph.RootPropRefGraph
import maryk.core.properties.references.AnyPropertyReference
import maryk.core.query.changes.Change
import maryk.core.query.changes.ObjectCreate
import maryk.core.query.changes.VersionedChanges
import maryk.datastore.memory.records.DataRecord

internal data class RecordVersionedChanges<DM : IsRootDataModel>(
    val record: DataRecord<DM>,
    val versionedChange: VersionedChanges
)

internal fun <DM : IsRootDataModel> DM.recordHistoryToVersionedChanges(
    select: RootPropRefGraph<DM>?,
    fromVersion: ULong,
    toVersion: ULong?,
    maxVersions: UInt,
    sortingKey: ByteArray?,
    historyRecords: List<DataRecord<DM>>,
    keepAllVersions: Boolean,
    projectedQualifiers: List<ByteArray>? = null,
): List<RecordVersionedChanges<DM>> {
    if (historyRecords.size == 1) {
        val record = historyRecords.single()
        val changes = recordToObjectChanges(
            select = select,
            fromVersion = fromVersion,
            toVersion = toVersion,
            maxVersions = UInt.MAX_VALUE,
            sortingKey = sortingKey,
            record = record,
            keepAllVersions = keepAllVersions,
            projectedQualifiers = projectedQualifiers,
        )?.changes.orEmpty()
        return singleRecordVersionedChanges(record, changes, maxVersions)
    }
    val allChanges = historyRecords.flatMap { historyRecord ->
        recordToObjectChanges(
            select = select,
            fromVersion = fromVersion,
            toVersion = toVersion,
            maxVersions = UInt.MAX_VALUE,
            sortingKey = sortingKey,
            record = historyRecord,
            keepAllVersions = keepAllVersions,
            projectedQualifiers = projectedQualifiers,
        )?.changes.orEmpty().map { RecordVersionedChanges(historyRecord, it) }
    }.sortedBy { it.versionedChange.version }

    if (allChanges.isEmpty()) {
        return emptyList()
    }

    val creationChanges = allChanges.filter { ObjectCreate in it.versionedChange.changes }
    val nonCreationChanges = allChanges.filterNot { ObjectCreate in it.versionedChange.changes }
    val keptNonCreationChanges = nonCreationChanges.takeLast(maxVersions.toInt())
    val laterChangedReferencesByRecord = keptNonCreationChanges
        .groupBy { it.record }
        .mapValues { (_, changes) ->
            changes.flatMapTo(mutableSetOf()) { recordChange ->
                recordChange.versionedChange.changes.flatMap { change ->
                    when (change) {
                        is Change -> change.referenceValuePairs.map { it.reference }
                        else -> emptyList()
                    }
                }
            }
        }

    val normalizedCreationChanges = creationChanges.map { creationChange ->
        val laterChangedReferences = laterChangedReferencesByRecord[creationChange.record].orEmpty()
        if (laterChangedReferences.isEmpty()) {
            creationChange
        } else {
            val filteredChanges = creationChange.versionedChange.changes.mapNotNull { change ->
                when (change) {
                    is Change -> change.referenceValuePairs
                        .filterNot { it.reference in laterChangedReferences }
                        .takeIf { it.isNotEmpty() }
                        ?.let { Change(*it.toTypedArray()) }
                    else -> change
                }
            }

            RecordVersionedChanges(
                record = creationChange.record,
                versionedChange = VersionedChanges(
                    version = creationChange.versionedChange.version,
                    changes = filteredChanges
                )
            )
        }
    }
    return (normalizedCreationChanges + keptNonCreationChanges).sortedBy { it.versionedChange.version }
}

/** The cell reader already orders one incarnation; only its retained deltas need normalization. */
private fun <DM : IsRootDataModel> singleRecordVersionedChanges(
    record: DataRecord<DM>,
    changes: List<VersionedChanges>,
    maxVersions: UInt,
): List<RecordVersionedChanges<DM>> {
    if (changes.isEmpty()) return emptyList()
    val limit = maxVersions.toInt()
    require(limit >= 0) { "Requested element count $limit is less than zero." }
    var firstKept = changes.size
    var retained = 0
    if (limit > 0) for (index in changes.indices.reversed()) {
        if (ObjectCreate !in changes[index].changes) {
            firstKept = index
            retained++
            if (retained == limit) break
        }
    }
    val laterChangedReferences = mutableSetOf<AnyPropertyReference>()
    for (index in firstKept until changes.size) {
        val versioned = changes[index]
        if (ObjectCreate in versioned.changes) continue
        for (change in versioned.changes) if (change is Change) {
            for (pair in change.referenceValuePairs) laterChangedReferences += pair.reference
        }
    }
    val result = ArrayList<RecordVersionedChanges<DM>>(minOf(changes.size.toLong(), retained.toLong() + 1).toInt())
    for ((index, versioned) in changes.withIndex()) {
        if (ObjectCreate in versioned.changes) {
            val normalized = if (laterChangedReferences.isEmpty()) versioned else versioned.copy(
                changes = versioned.changes.mapNotNull { change ->
                    when (change) {
                        is Change -> change.referenceValuePairs
                            .filterNot { it.reference in laterChangedReferences }
                            .takeIf { it.isNotEmpty() }
                            ?.let { Change(*it.toTypedArray()) }
                        else -> change
                    }
                },
            )
            result += RecordVersionedChanges(record, normalized)
        } else if (index >= firstKept) {
            result += RecordVersionedChanges(record, versioned)
        }
    }
    return result
}
