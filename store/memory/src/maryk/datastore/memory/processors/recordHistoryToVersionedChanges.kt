package maryk.datastore.memory.processors

import maryk.core.models.IsRootDataModel
import maryk.core.properties.graph.RootPropRefGraph
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
    historyRecords: List<DataRecord<DM>>
): List<RecordVersionedChanges<DM>> {
    val allChanges = historyRecords.flatMap { historyRecord ->
        recordToObjectChanges(
            select = select,
            fromVersion = fromVersion,
            toVersion = toVersion,
            maxVersions = UInt.MAX_VALUE,
            sortingKey = sortingKey,
            record = historyRecord
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
