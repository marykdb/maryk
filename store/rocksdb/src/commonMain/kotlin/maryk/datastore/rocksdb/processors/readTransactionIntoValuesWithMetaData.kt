package maryk.datastore.rocksdb.processors

import maryk.core.exceptions.RequestException
import maryk.core.extensions.bytes.initIntByVar
import maryk.core.extensions.bytes.initULong
import maryk.core.extensions.bytes.toULong
import maryk.core.models.IsRootValuesDataModel
import maryk.core.processors.datastore.StorageTypeEnum.Embed
import maryk.core.processors.datastore.StorageTypeEnum.ListSize
import maryk.core.processors.datastore.StorageTypeEnum.MapSize
import maryk.core.processors.datastore.StorageTypeEnum.ObjectDelete
import maryk.core.processors.datastore.StorageTypeEnum.SetSize
import maryk.core.processors.datastore.StorageTypeEnum.TypeValue
import maryk.core.processors.datastore.StorageTypeEnum.Value
import maryk.core.processors.datastore.convertStorageToValues
import maryk.core.properties.PropertyDefinitions
import maryk.core.properties.definitions.IsSimpleValueDefinition
import maryk.core.properties.graph.RootPropRefGraph
import maryk.core.properties.types.Key
import maryk.core.properties.types.TypedValue
import maryk.core.query.ValuesWithMetaData
import maryk.core.values.Values
import maryk.datastore.rocksdb.HistoricTableColumnFamilies
import maryk.datastore.rocksdb.TableColumnFamilies
import maryk.lib.extensions.compare.matchPart
import maryk.rocksdb.ReadOptions
import maryk.rocksdb.Transaction

/**
 * Read values for [key] from a [transaction] with [readOptions] from [columnFamilies]
 * to a ValuesWithMeta object. Filter results on [select] and use [toVersion]
 */
internal fun <DM : IsRootValuesDataModel<P>, P : PropertyDefinitions> DM.readTransactionIntoValuesWithMetaData(
    transaction: Transaction,
    readOptions: ReadOptions,
    columnFamilies: TableColumnFamilies,
    key: Key<DM>,
    select: RootPropRefGraph<P>?,
    toVersion: ULong?
): ValuesWithMetaData<DM, P>? {
    val firstVersion = transaction.get(columnFamilies.table, readOptions, key.bytes)?.toULong()
        ?: return null
    var maxVersion = firstVersion
    val isDeleted: Boolean

    val values: Values<DM, P> = if (toVersion == null) {
        val iterator = transaction.getIterator(readOptions, columnFamilies.table)

        isDeleted = transaction.get(columnFamilies.table, readOptions, byteArrayOf(*key.bytes, SOFT_DELETE_INDICATOR))?.last() == TRUE

        // Start at begin of record
        iterator.seek(key.bytes)
        iterator.key()

        val getQualifier = {
            iterator.next()
            if (!iterator.isValid()) {
                null
            } else {
                var qualifier: ByteArray? = iterator.key()
                if (qualifier?.size == key.bytes.size) {
                    iterator.next()
                    qualifier = if (!iterator.isValid()) {
                        null
                    } else {
                        iterator.key()
                    }
                }

                if (qualifier != null && qualifier.matchPart(0, key.bytes)) {
                    qualifier.copyOfRange(key.bytes.size, qualifier.size)
                } else null
            }
        }

        var index: Int
        this.convertStorageToValues(
            getQualifier = getQualifier,
            select = select,
            processValue = { storageType, definition ->
                when (storageType) {
                    ObjectDelete -> null
                    Value -> {
                        val valueBytes = iterator.value()
                        index = 0
                        maxVersion = maxOf(initULong({ valueBytes[index++] }), maxVersion)

                        Value.castDefinition(definition).readStorageBytes(valueBytes.size - index) {
                            valueBytes[index++]
                        }
                    }
                    ListSize -> {
                        val valueBytes = iterator.value()
                        index = 0
                        maxVersion = maxOf(initULong({ valueBytes[index++] }), maxVersion)

                        initIntByVar { valueBytes[index++] }
                    }
                    SetSize -> {
                        val valueBytes = iterator.value()
                        index = 0
                        maxVersion = maxOf(initULong({ valueBytes[index++] }), maxVersion)

                        initIntByVar { valueBytes[index++] }
                    }
                    MapSize -> {
                        val valueBytes = iterator.value()
                        index = 0
                        maxVersion = maxOf(initULong({ valueBytes[index++] }), maxVersion)

                        initIntByVar { valueBytes[index++] }
                    }
                    TypeValue -> {
                        val valueBytes = iterator.value()
                        index = 0
                        val reader = { valueBytes[index++] }

                        maxVersion = maxOf(initULong(reader), maxVersion)
                        val typeDefinition = TypeValue.castDefinition(definition)

                        val indicatorByte = reader()

                        val type = typeDefinition.typeEnum.readStorageBytes(typeDefinition.typeEnum.byteSize, reader)

                        if (indicatorByte == COMPLEX_TYPE_INDICATOR) {
                            TypedValue(type, Unit)
                        } else {
                            val valueDefinition = typeDefinition.definition(type) as IsSimpleValueDefinition<*, *>
                            valueDefinition.readStorageBytes(valueBytes.size - index, reader)
                        }
                    }
                    Embed -> { Unit }
                }
            }
        ).also {
            iterator.close()
        }
    } else {
        if (columnFamilies !is HistoricTableColumnFamilies) {
            throw RequestException("No historic table present so cannot use `toVersion`")
        }

//        val iterator = transaction.getIterator(readOptions, columnFamilies.historic.table)
//
//        this.convertStorageToValues(
//            getQualifier = { record.values.getOrNull(++valueIndex)?.reference },
//            select = select,
//            processValue = { _, _ ->
//
//            }
//        )
        TODO("IMPLEMENT")
    }

    if (values.size == 0) {
        // Return null if no ValueItems were found
        return null
    }

    return ValuesWithMetaData(
        key = key,
        values = values,
        isDeleted = isDeleted,
        firstVersion = firstVersion,
        lastVersion = maxVersion
    )
}
