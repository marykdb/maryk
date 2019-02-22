package maryk.core.processors.datastore

import maryk.core.models.IsRootValuesDataModel
import maryk.core.properties.IsPropertyContext
import maryk.core.properties.definitions.IsChangeableValueDefinition
import maryk.core.properties.definitions.IsComparableDefinition
import maryk.core.properties.definitions.IsSerializablePropertyDefinition
import maryk.core.properties.definitions.index.Multiple
import maryk.core.properties.references.IsFixedBytesPropertyReference
import maryk.core.properties.references.IsIndexablePropertyReference
import maryk.core.properties.references.IsPropertyReference
import maryk.core.query.filters.And
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

/** Convert [filter] for [dataModel] into [listOfKeyParts] */
fun convertFilterToKeyPartsToMatch(
    dataModel: IsRootValuesDataModel<*>,
    filter: IsFilter?,
    listOfKeyParts: MutableList<IsKeyPartialToMatch>,
    listOfUniqueFilters: MutableList<UniqueToMatch>
) {
    when (filter) {
        null -> {} // Skip
        is Equals -> walkFilterReferencesAndValues(
            filter,
            dataModel,
            listOfUniqueFilters::add
        ) { index, byteArray ->
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
            getKeyDefinitionOrNull(dataModel, reference) { index, _, keyDefinition ->
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
            getKeyDefinitionOrNull(dataModel, reference) { index, _, keyDefinition ->
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

            // Add all unique matchers for every item in valueIn
            reference.comparablePropertyDefinition.let {
                if (it is IsComparableDefinition<*, *> && it.unique) {
                    for (uniqueToMatch in value) {
                        listOfUniqueFilters.add(
                            createUniqueToMatch(reference, it, uniqueToMatch)
                        )
                    }
                }
            }
        }
        is And -> {
            for (aFilter in filter.filters) {
                convertFilterToKeyPartsToMatch(dataModel, aFilter, listOfKeyParts, listOfUniqueFilters)
            }
        }
        else -> { /** Skip since other filters are not supported for key scan ranges*/ }
    }
}

/** Convert [value] with [keyDefinition] into a key ByteArray */
private fun convertValueToKeyBytes(
    keyDefinition: IsIndexablePropertyReference<Any>,
    value: Any
): ByteArray {
    var byteReadIndex = 0
    val byteArray = ByteArray(
        keyDefinition.calculateStorageByteLength(value)
    )
    keyDefinition.writeStorageBytes(value) {
        byteArray[byteReadIndex++] = it
    }
    return byteArray
}

/** Walk [referenceValuePairs] using [dataModel] into [handleKeyBytes] */
private fun <T: Any> walkFilterReferencesAndValues(
    referenceValuePairs: IsReferenceValuePairsFilter<T>,
    dataModel: IsRootValuesDataModel<*>,
    handleUniqueMatchers : ((UniqueToMatch) -> Boolean)? = null,
    handleKeyBytes: (Int, ByteArray) -> Unit
) {
    for ((reference, value) in referenceValuePairs.referenceValuePairs) {
        getKeyDefinitionOrNull(dataModel, reference) { _, byteIndex, keyDefinition ->
            val byteArray = convertValueToKeyBytes(keyDefinition, value)

            handleKeyBytes(byteIndex, byteArray)
        }

        // Add unique to match if
        reference.comparablePropertyDefinition.let {
            if (it is IsComparableDefinition<*, *> && it.unique) {
                handleUniqueMatchers?.invoke(
                    createUniqueToMatch(reference, it, value)
                )
            }
        }
    }
}

@Suppress("UNCHECKED_CAST")
private fun <T : Any> createUniqueToMatch(
    reference: IsPropertyReference<T, IsChangeableValueDefinition<T, IsPropertyContext>, *>,
    it: IsSerializablePropertyDefinition<T, IsPropertyContext>,
    value: T
) = UniqueToMatch(
    reference.toStorageByteArray(),
    it as IsComparableDefinition<Comparable<Any>, IsPropertyContext>,
    value as Comparable<*>
)

/** Get key definition by [reference] and [processKeyDefinitionIfFound] using [dataModel] or null if not part of key */
private fun <T: Any> getKeyDefinitionOrNull(
    dataModel: IsRootValuesDataModel<*>,
    reference: IsPropertyReference<out T, IsChangeableValueDefinition<out T, IsPropertyContext>, *>,
    processKeyDefinitionIfFound: (Int, Int, IsIndexablePropertyReference<Any>) -> Unit
){
    when(val keyDefinition = dataModel.keyDefinition) {
        is Multiple -> {
            for ((index, keyDef) in keyDefinition.references.withIndex()) {
                if (keyDef.isForPropertyReference(reference)) {
                    @Suppress("UNCHECKED_CAST")
                    processKeyDefinitionIfFound(index, dataModel.keyIndices[index], keyDef as IsIndexablePropertyReference<Any>)
                    break
                }
            }
        }
        is IsFixedBytesPropertyReference<*> -> {
            if (keyDefinition.isForPropertyReference(reference)) {
                @Suppress("UNCHECKED_CAST")
                processKeyDefinitionIfFound(0, 0, keyDefinition as IsFixedBytesPropertyReference<Any>)
            }
        }
        else -> throw Exception("Impossible option $keyDefinition for keyDefinition")
    }

}
