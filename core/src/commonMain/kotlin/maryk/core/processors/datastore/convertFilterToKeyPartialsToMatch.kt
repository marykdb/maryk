package maryk.core.processors.datastore

import maryk.core.exceptions.TypeException
import maryk.core.properties.IsPropertyContext
import maryk.core.properties.definitions.IsChangeableValueDefinition
import maryk.core.properties.definitions.IsComparableDefinition
import maryk.core.properties.definitions.IsSerializablePropertyDefinition
import maryk.core.properties.definitions.index.IsIndexable
import maryk.core.properties.definitions.index.Multiple
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
import maryk.core.query.pairs.ReferenceValuePair
import maryk.lib.extensions.compare.compareTo

/**
 * Convert [filter] for [indexable] into [listOfIndexParts], [listOfEqualPairs] and [listOfUniqueFilters]
 */
fun convertFilterToKeyPartsToMatch(
    indexable: IsIndexable,
    convertIndex: ((Int) -> Int)?,
    filter: IsFilter?,
    listOfIndexParts: MutableList<IsIndexPartialToMatch>,
    listOfEqualPairs: MutableList<ReferenceValuePair<Any>>? = null,
    listOfUniqueFilters: MutableList<UniqueToMatch>? = null
) {
    when (filter) {
        null -> Unit // Skip
        is Equals -> {
            listOfEqualPairs?.addAll(filter.referenceValuePairs)
            walkFilterReferencesAndValues(
                filter,
                indexable,
                listOfUniqueFilters
            ) { index, byteArray ->
                val keyIndex = convertIndex?.invoke(index)
                listOfIndexParts.add(
                    IndexPartialToMatch(index, keyIndex, byteArray)
                )
            }
        }
        is GreaterThan -> walkFilterReferencesAndValues(filter, indexable) { index, byteArray ->
            val keyIndex = convertIndex?.invoke(index)
            listOfIndexParts.add(
                IndexPartialToBeBigger(index, keyIndex, byteArray, false)
            )
        }
        is GreaterThanEquals -> walkFilterReferencesAndValues(filter, indexable) { index, byteArray ->
            val keyIndex = convertIndex?.invoke(index)
            listOfIndexParts.add(
                IndexPartialToBeBigger(index, keyIndex, byteArray, true)
            )
        }
        is LessThan -> walkFilterReferencesAndValues(filter, indexable) { index, byteArray ->
            val keyIndex = convertIndex?.invoke(index)
            listOfIndexParts.add(
                IndexPartialToBeSmaller(index, keyIndex, byteArray, false)
            )
        }
        is LessThanEquals -> walkFilterReferencesAndValues(filter, indexable) { index, byteArray ->
            val keyIndex = convertIndex?.invoke(index)
            listOfIndexParts.add(
                IndexPartialToBeSmaller(index, keyIndex, byteArray, true)
            )
        }
        is Range -> for ((reference, value) in filter.referenceRangePairs) {
            getDefinitionOrNull(indexable, reference) { index, keyDefinition ->
                val keyIndex = convertIndex?.invoke(index)
                val fromBytes = convertValueToKeyBytes(keyDefinition, value.from)
                val toBytes = convertValueToKeyBytes(keyDefinition, value.to)
                listOfIndexParts.add(
                    IndexPartialToBeSmaller(index, keyIndex, fromBytes, value.inclusiveFrom)
                )
                listOfIndexParts.add(
                    IndexPartialToBeBigger(index, keyIndex, toBytes, value.inclusiveTo)
                )
            }
        }
        is ValueIn -> for ((reference, value) in filter.referenceValuePairs) {
            getDefinitionOrNull(indexable, reference) { index, keyDefinition ->
                val keyIndex = convertIndex?.invoke(index)
                val list = ArrayList<ByteArray>(value.size)
                for (setValue in value) {
                    list.add(
                        convertValueToKeyBytes(keyDefinition, setValue)
                    )
                }
                list.sortWith(object : Comparator<ByteArray> {
                    override fun compare(a: ByteArray, b: ByteArray) = a.compareTo(b)
                })
                listOfIndexParts.add(
                    IndexPartialToBeOneOf(index, keyIndex, list)
                )
            }

            // Add all unique matchers for every item in valueIn
            listOfUniqueFilters?.let {
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
        }
        is And -> {
            for (aFilter in filter.filters) {
                convertFilterToKeyPartsToMatch(indexable, convertIndex, aFilter, listOfIndexParts, listOfEqualPairs, listOfUniqueFilters)
            }
        }
        else -> {
            /** Skip since other filters are not supported for key scan ranges*/
        }
    }
}

/** Convert [value] with [indexableRef] into a key ByteArray */
private fun convertValueToKeyBytes(
    indexableRef: IsIndexablePropertyReference<Any>,
    value: Any
): ByteArray {
    var byteReadIndex = 0
    val byteArray = ByteArray(
        indexableRef.calculateStorageByteLength(value)
    )
    indexableRef.writeStorageBytes(value) {
        byteArray[byteReadIndex++] = it
    }
    return byteArray
}

/** Walk [referenceValuePairs] using [indexable] into [handleKeyBytes] */
private fun <T : Any> walkFilterReferencesAndValues(
    referenceValuePairs: IsReferenceValuePairsFilter<T>,
    indexable: IsIndexable,
    listOfUniqueFilters: MutableList<UniqueToMatch>? = null,
    handleKeyBytes: (Int, ByteArray) -> Unit
) {
    for ((reference, value) in referenceValuePairs.referenceValuePairs) {
        getDefinitionOrNull(indexable, reference) { index, keyDefinition ->
            val byteArray = convertValueToKeyBytes(keyDefinition, value)

            handleKeyBytes(index, byteArray)
        }

        // Add unique to match if handler was passed
        listOfUniqueFilters?.let {
            reference.comparablePropertyDefinition.let {
                if (it is IsComparableDefinition<*, *> && it.unique) {
                    listOfUniqueFilters.add(
                        createUniqueToMatch(reference, it, value)
                    )
                }
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

/** Get key definition by [reference] and [processKeyDefinitionWhenFound] using [indexable] or null if not part of key */
private fun <T : Any> getDefinitionOrNull(
    indexable: IsIndexable,
    reference: IsPropertyReference<out T, IsChangeableValueDefinition<out T, IsPropertyContext>, *>,
    processKeyDefinitionWhenFound: (Int, IsIndexablePropertyReference<Any>) -> Unit
) {
    when (indexable) {
        is Multiple -> {
            for ((index, keyDef) in indexable.references.withIndex()) {
                if (keyDef.isForPropertyReference(reference)) {
                    @Suppress("UNCHECKED_CAST")
                    processKeyDefinitionWhenFound(
                        index,
                        keyDef as IsIndexablePropertyReference<Any>
                    )
                    break
                }
            }
        }
        is IsIndexablePropertyReference<*> -> {
            if (indexable.isForPropertyReference(reference)) {
                @Suppress("UNCHECKED_CAST")
                processKeyDefinitionWhenFound(0, indexable as IsIndexablePropertyReference<Any>)
            }
        }
        else -> throw TypeException("Impossible option $indexable for keyDefinition")
    }
}
