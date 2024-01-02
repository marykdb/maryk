package maryk.datastore.hbase.processors

import maryk.core.exceptions.StorageException
import maryk.core.extensions.bytes.initIntByVar
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
import maryk.datastore.hbase.dataColumnFamily
import maryk.datastore.shared.readValue
import maryk.datastore.hbase.trueIndicator
import org.apache.hadoop.hbase.Cell
import org.apache.hadoop.hbase.client.Result

/** Process values for [key] from transaction to a DataObjectWithChanges object */
internal fun <DM : IsRootDataModel> DM.readTransactionIntoObjectChanges(
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

    val allCellIterator = result.listCells().filter {
        it.familyArray[it.familyOffset] == dataColumnFamily.first()
    }.iterator()

    var currentCell: Cell? = null

    // Will start by going to next key so will miss the creation timestamp
    val getQualifier: (((Int) -> Byte, Int) -> Unit) -> Boolean = { qualifierReader ->
        allCellIterator.hasNext().also {
            if (it) {
                currentCell = allCellIterator.next()
                currentVersion = currentCell!!.timestamp.toULong()

                qualifierReader(
                    { currentCell!!.qualifierArray[currentCell!!.qualifierOffset + it] },
                    currentCell!!.qualifierLength
                )
            } else {
                currentCell = null
            }
        }
    }

    var index: Int
    changes = this.readStorageToChanges(
        getQualifier = getQualifier,
        select = select,
        creationVersion = if (creationVersion > fromVersion) creationVersion else null,
        processValue = { storageType, reference, valueWithVersionReader ->
            val value = cachedRead(reference, currentVersion) {
                when (storageType) {
                    ObjectDelete -> {
                        if (currentCell!!.qualifierLength == 1 && currentCell!!.qualifierArray[currentCell!!.qualifierOffset] == 0.toByte()) {
                            currentCell!!.valueArray[currentCell!!.valueOffset] == trueIndicator.first()
                        } else null
                    }
                    Value -> {
                        val valueBytes = currentCell!!.valueArray
                        index = currentCell!!.valueOffset
                        val reader = { valueBytes[currentCell!!.valueOffset + index++] }

                        val definition = (reference.propertyDefinition as? IsDefinitionWrapper<*, *, *, *>)?.definition
                            ?: reference.propertyDefinition
                        readValue(definition, reader) {
                            currentCell!!.valueLength - index
                        }
                    }
                    ListSize -> {
                        val valueBytes = currentCell!!.valueArray
                        index = currentCell!!.valueOffset
                        initIntByVar { valueBytes[index++] }
                    }
                    SetSize -> {
                        val valueBytes = currentCell!!.valueArray
                        index = currentCell!!.valueOffset
                        initIntByVar { valueBytes[index++] }
                    }
                    MapSize -> {
                        val valueBytes = currentCell!!.valueArray
                        index = currentCell!!.valueOffset
                        initIntByVar { valueBytes[index++] }
                    }
                    Embed -> {}
                    TypeValue -> throw StorageException("Not used in direct encoding")
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
