package maryk.datastore.terminal.driver

import maryk.core.extensions.bytes.initIntByVar
import maryk.core.models.RootDataModel
import maryk.core.models.emptyValues
import maryk.core.processors.datastore.StorageTypeEnum
import maryk.core.processors.datastore.StorageTypeEnum.Embed
import maryk.core.processors.datastore.StorageTypeEnum.ListSize
import maryk.core.processors.datastore.StorageTypeEnum.MapSize
import maryk.core.processors.datastore.StorageTypeEnum.ObjectDelete
import maryk.core.processors.datastore.StorageTypeEnum.SetSize
import maryk.core.processors.datastore.StorageTypeEnum.Value
import maryk.core.processors.datastore.readStorageToValues
import maryk.core.properties.definitions.wrapper.IsDefinitionWrapper
import maryk.core.properties.references.IsPropertyReferenceForCache
import maryk.core.values.Values
import maryk.datastore.shared.readValue

internal data class StorageEntry(
    val qualifier: ByteArray,
    val valueBytes: ByteArray,
    val valueOffset: Int,
)

internal fun RootDataModel<*>.decodeStorageEntries(entries: List<StorageEntry>): Values<*> {
    if (entries.isEmpty()) {
        return this.emptyValues()
    }

    var currentEntry: StorageEntry = entries.first()
    val iterator = entries.iterator()

    return this.readStorageToValues(
        getQualifier = { handler ->
            if (!iterator.hasNext()) {
                false
            } else {
                currentEntry = iterator.next()
                val qualifier = currentEntry.qualifier
                handler({ index -> qualifier[index] }, qualifier.size)
                true
            }
        },
        select = null,
        processValue = { storageType, reference ->
            decodeValueFromEntry(currentEntry, storageType, reference)
        },
    )
}

private fun decodeValueFromEntry(
    entry: StorageEntry,
    storageType: StorageTypeEnum<*>,
    reference: IsPropertyReferenceForCache<*, *>,
): Any? {
    val bytes = entry.valueBytes
    var index = entry.valueOffset

    return when (storageType) {
        ObjectDelete -> null
        ListSize, SetSize, MapSize -> initIntByVar { bytes[index++] }
        Embed -> null
        Value -> {
            val definition = (reference.propertyDefinition as? IsDefinitionWrapper<*, *, *, *>)?.definition
                ?: reference.propertyDefinition
            readValue(definition, { bytes[index++] }) { bytes.size - index }
        }
        else -> null
    }
}

