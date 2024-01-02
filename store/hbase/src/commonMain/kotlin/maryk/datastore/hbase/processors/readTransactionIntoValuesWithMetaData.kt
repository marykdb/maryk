package maryk.datastore.hbase.processors

import maryk.core.exceptions.StorageException
import maryk.core.extensions.bytes.initIntByVar
import maryk.core.models.IsRootDataModel
import maryk.core.models.values
import maryk.core.processors.datastore.StorageTypeEnum.Embed
import maryk.core.processors.datastore.StorageTypeEnum.ListSize
import maryk.core.processors.datastore.StorageTypeEnum.MapSize
import maryk.core.processors.datastore.StorageTypeEnum.ObjectDelete
import maryk.core.processors.datastore.StorageTypeEnum.SetSize
import maryk.core.processors.datastore.StorageTypeEnum.TypeValue
import maryk.core.processors.datastore.StorageTypeEnum.Value
import maryk.core.processors.datastore.readStorageToValues
import maryk.core.properties.definitions.wrapper.IsDefinitionWrapper
import maryk.core.properties.graph.RootPropRefGraph
import maryk.core.properties.references.IsPropertyReferenceForCache
import maryk.core.properties.types.Key
import maryk.core.query.ValuesWithMetaData
import maryk.core.values.EmptyValueItems
import maryk.core.values.Values
import maryk.datastore.hbase.dataColumnFamily
import maryk.datastore.hbase.trueIndicator
import maryk.datastore.shared.readValue
import org.apache.hadoop.hbase.Cell
import org.apache.hadoop.hbase.client.Result

/**
 * Read values for [key] from an [iterator] to a ValuesWithMeta object.
 * Filter results on [select]
 */
internal fun <DM : IsRootDataModel> DM.readTransactionIntoValuesWithMetaData(
    result: Result,
    creationVersion: ULong,
    key: Key<DM>,
    select: RootPropRefGraph<DM>?,
    cachedRead: (IsPropertyReferenceForCache<*, *>, ULong, () -> Any?) -> Any?
): ValuesWithMetaData<DM>? {
    var maxVersion = creationVersion
    var isDeleted = false

    val values: Values<DM> = if (select != null && select.properties.isEmpty()) {
        // Don't read the values if no values are selected
        this.values(null) { EmptyValueItems }
    } else {
        var currentVersion = 0uL

        val allCellIterator = result.listCells().filter {
            it.familyArray[it.familyOffset] == dataColumnFamily.first()
        }.iterator()

        if (!allCellIterator.hasNext()) {
            this.values(null) { EmptyValueItems }
        } else {
            var currentCell: Cell? = null
            // Will start by going to next key so will miss the creation timestamp
            val getQualifier: (((Int) -> Byte, Int) -> Unit) -> Boolean = { qualifierReader ->
                allCellIterator.hasNext().also {
                    if (it) {
                        currentCell = allCellIterator.next()

                        qualifierReader(
                            { currentCell!!.qualifierArray[currentCell!!.qualifierOffset + it] },
                            currentCell!!.qualifierLength
                        )

                        currentVersion = currentCell!!.timestamp.toULong()
                        maxVersion = maxOf(currentVersion, maxVersion)
                    } else {
                        currentCell = null
                    }
                }
            }

            var index: Int
            this.readStorageToValues(
                getQualifier = getQualifier,
                select = select,
                processValue = { storageType, reference ->
                    cachedRead(reference, currentVersion) {
                        if (currentCell == null) {
                            null
                        } else {
                            when (storageType) {
                                ObjectDelete -> {
                                    if (currentCell!!.qualifierLength == 1 && currentCell!!.qualifierArray[currentCell!!.qualifierOffset] == 0.toByte()) {
                                        currentVersion = maxOf(currentCell!!.timestamp.toULong(), maxVersion)
                                        isDeleted = currentCell!!.valueArray[currentCell!!.valueOffset] == trueIndicator.first()
                                        isDeleted
                                    } else {
                                        currentVersion = 0uL
                                        null
                                    }
                                }
                                Value -> {
                                    val valueBytes = currentCell!!.valueArray
                                    index = 0
                                    val reader = { valueBytes[currentCell!!.valueOffset + index++] }

                                    val definition =
                                        (reference.propertyDefinition as? IsDefinitionWrapper<*, *, *, *>)?.definition
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
                                Embed -> Unit
                                TypeValue -> throw StorageException("Not used in direct encoding")
                            }
                        }
                    }
                }
            )
        }

    }

    // Return null if no values where found but values where selected
    if (values.size == 0 && (select == null || select.properties.isNotEmpty())) {
        // Return null if no ValueItems were found
        return null
    }

    return ValuesWithMetaData(
        key = key,
        values = values,
        isDeleted = isDeleted,
        firstVersion = creationVersion,
        lastVersion = maxVersion
    )
}
