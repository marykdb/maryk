package maryk.datastore.hbase.processors

import maryk.core.exceptions.StorageException
import maryk.core.models.IsRootDataModel
import maryk.core.processors.datastore.StorageTypeEnum.Embed
import maryk.core.processors.datastore.StorageTypeEnum.ListSize
import maryk.core.processors.datastore.StorageTypeEnum.MapSize
import maryk.core.processors.datastore.StorageTypeEnum.ObjectDelete
import maryk.core.processors.datastore.StorageTypeEnum.SetSize
import maryk.core.processors.datastore.StorageTypeEnum.TypeValue
import maryk.core.processors.datastore.StorageTypeEnum.Value
import maryk.core.processors.datastore.readStorageToChanges
import maryk.core.properties.definitions.wrapper.IsDefinitionWrapper
import maryk.core.properties.graph.RootPropRefGraph
import maryk.core.properties.references.IsPropertyReferenceForCache
import maryk.core.properties.types.Bytes
import maryk.core.properties.types.Key
import maryk.core.query.changes.DataObjectVersionedChange
import maryk.core.query.changes.VersionedChanges
import maryk.datastore.hbase.helpers.readCountValue
import maryk.datastore.hbase.helpers.readValue
import maryk.datastore.hbase.trueIndicator
import org.apache.hadoop.hbase.Cell
import org.apache.hadoop.hbase.client.Result

/** Process values for [key] from result to a DataObjectWithChanges object */
internal fun <DM : IsRootDataModel> DM.readResultIntoObjectChanges(
    result: Result,
    creationVersion: ULong,
    key: Key<DM>,
    select: RootPropRefGraph<DM>?,
    fromVersion: ULong,
    sortingKey: ByteArray?,
    cachedRead: (IsPropertyReferenceForCache<*, *>, ULong, () -> Any?) -> Any?
): DataObjectVersionedChange<DM>? {
    val changes: List<VersionedChanges>

    var currentVersion: ULong = creationVersion

    val allCellIterator = result.listCells().iterator()

    var currentCell: Cell? = null

    // Will start by going to next key so will miss the creation timestamp
    val getQualifier: (((Int) -> Byte, Int) -> Unit) -> Boolean = { qualifierReader ->
        allCellIterator.hasNext().also {
            if (it) {
                val cellToRead = allCellIterator.next()
                currentCell = cellToRead
                currentVersion = cellToRead.timestamp.toULong()

                qualifierReader(
                    { cellToRead.qualifierArray[cellToRead.qualifierOffset + it] },
                    cellToRead.qualifierLength
                )
            } else {
                currentCell = null
            }
        }
    }

    changes = this.readStorageToChanges(
        getQualifier = getQualifier,
        select = select,
        creationVersion = if (creationVersion > fromVersion) creationVersion else null,
        processValue = { storageType, reference, valueWithVersionReader ->
            val value = cachedRead(reference, currentVersion) {
                val cell = currentCell
                if (cell == null) {
                    null
                } else {
                    when (storageType) {
                        ObjectDelete -> {
                            if (cell.qualifierLength == 1 && cell.qualifierArray[cell.qualifierOffset] == 0.toByte()) {
                                cell.valueArray[cell.valueOffset] == trueIndicator.first()
                            } else null
                        }
                        Value -> {
                            val definition =
                                (reference.propertyDefinition as? IsDefinitionWrapper<*, *, *, *>)?.definition
                                    ?: reference.propertyDefinition
                            cell.readValue(definition)
                        }
                        ListSize -> cell.readCountValue()
                        SetSize -> cell.readCountValue()
                        MapSize -> cell.readCountValue()
                        Embed -> Unit
                        TypeValue -> throw StorageException("Not used in direct encoding")
                    }
                }
            }
            valueWithVersionReader(currentVersion, value)
        }
    )

    if (changes.isEmpty()) {
        // Return null if no ValueItems were found
        return null
    }
    return DataObjectVersionedChange(
        key = key,
        sortingKey = sortingKey?.let(::Bytes),
        changes = changes
    )
}
