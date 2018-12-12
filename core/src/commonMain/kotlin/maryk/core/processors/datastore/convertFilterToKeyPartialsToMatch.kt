package maryk.core.processors.datastore

import maryk.core.models.IsRootValuesDataModel
import maryk.core.properties.IsPropertyContext
import maryk.core.properties.definitions.FixedBytesProperty
import maryk.core.properties.definitions.wrapper.IsValuePropertyDefinitionWrapper
import maryk.core.properties.references.IsPropertyReference
import maryk.core.query.filters.Equals
import maryk.core.query.filters.GreaterThan
import maryk.core.query.filters.GreaterThanEquals
import maryk.core.query.filters.IsFilter
import maryk.core.query.filters.IsReferenceValuePairsFilter
import maryk.core.query.filters.LessThan
import maryk.core.query.filters.LessThanEquals
import maryk.core.query.filters.Range
import maryk.core.query.filters.ValueIn
import maryk.lib.extensions.compare.compareTo

/** Convert key parts to match for [filter] for [dataModel] into [listOfKeyParts] */
fun convertFilterToKeyPartsToMatch(
    dataModel: IsRootValuesDataModel<*>,
    filter: IsFilter?,
    listOfKeyParts: MutableList<IsKeyPartialToMatch>
) {
    when (filter) {
        null -> {} // Skip
        is Equals -> walkFilterReferencesAndValues(filter, dataModel) { index, byteArray ->
            listOfKeyParts.add(
                KeyPartialToMatch(index, byteArray)
            )
        }
        is GreaterThan -> walkFilterReferencesAndValues(filter, dataModel) { index, byteArray ->
            listOfKeyParts.add(
                KeyPartialToBeBigger(index, byteArray, false)
            )
        }
        is GreaterThanEquals -> walkFilterReferencesAndValues(filter, dataModel) { index, byteArray ->
            listOfKeyParts.add(
                KeyPartialToBeBigger(index, byteArray, true)
            )
        }
        is LessThan -> walkFilterReferencesAndValues(filter, dataModel) { index, byteArray ->
            listOfKeyParts.add(
                KeyPartialToBeSmaller(index, byteArray, false)
            )
        }
        is LessThanEquals -> walkFilterReferencesAndValues(filter, dataModel) { index, byteArray ->
            listOfKeyParts.add(
                KeyPartialToBeSmaller(index, byteArray, true)
            )
        }
        is Range -> for ((reference, value) in filter.referenceRangePairs) {
            getKeyDefinitionOrNull(dataModel, reference) { index, keyDefinition ->
                val fromBytes = convertValueToKeyBytes(keyDefinition, value.from)
                val toBytes = convertValueToKeyBytes(keyDefinition, value.to)
                listOfKeyParts.add(
                    KeyPartialToBeSmaller(index, fromBytes, value.inclusiveFrom)
                )
                listOfKeyParts.add(
                    KeyPartialToBeBigger(index, toBytes, value.inclusiveTo)
                )
            }
        }
        is ValueIn -> for ((reference, value) in filter.referenceValuePairs) {
            getKeyDefinitionOrNull(dataModel, reference) { index, keyDefinition ->
                val list = ArrayList<ByteArray>(value.size)
                for (setValue in value) {
                    list.add(
                        convertValueToKeyBytes(keyDefinition, setValue)
                    )
                }
                list.sortWith(object : Comparator<ByteArray> {
                    override fun compare(a : ByteArray, b: ByteArray) = a.compareTo(b)
                })
                listOfKeyParts.add(
                    KeyPartialToBeOneOf(index, list)
                )
            }
        }
        else -> { /** Skip since other filters are not supported for key scan ranges*/ }
    }
}

/** Convert [value] with [keyDefinition] into a key ByteArray */
private fun convertValueToKeyBytes(
    keyDefinition: FixedBytesProperty<Any>,
    value: Any
): ByteArray {
    var byteReadIndex = 0
    val byteArray = ByteArray(keyDefinition.byteSize)
    keyDefinition.writeStorageBytes(value) {
        byteArray[byteReadIndex++] = it
    }
    return byteArray
}

/** Walk [referenceValuePairs] using [dataModel] into [handleBytes] */
private fun <T: Any> walkFilterReferencesAndValues(
    referenceValuePairs: IsReferenceValuePairsFilter<T>,
    dataModel: IsRootValuesDataModel<*>,
    handleBytes: (Int, ByteArray) -> Unit
) {
    for ((reference, value) in referenceValuePairs.referenceValuePairs) {
        getKeyDefinitionOrNull(dataModel, reference) { index, keyDefinition ->
            val byteArray = convertValueToKeyBytes(keyDefinition, value)

            handleBytes(dataModel.keyIndices[index], byteArray)
        }
    }
}

/** Get key definition by [reference] and [processKeyDefinitionIfFound] using [dataModel] */
private fun <T: Any> getKeyDefinitionOrNull(
    dataModel: IsRootValuesDataModel<*>,
    reference: IsPropertyReference<out T, IsValuePropertyDefinitionWrapper<out T, *, IsPropertyContext, *>, *>,
    processKeyDefinitionIfFound: (Int, FixedBytesProperty<Any>) -> Unit
){
    for ((index, keyDef) in dataModel.keyDefinitions.withIndex()) {
        if (keyDef.isForPropertyReference(reference)) {
            @Suppress("UNCHECKED_CAST")
            processKeyDefinitionIfFound(index, keyDef as FixedBytesProperty<Any>)
            break
        }
    }
}
