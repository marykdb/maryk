package maryk.core.processors.datastore

import maryk.core.exceptions.TypeException
import maryk.core.properties.IsPropertyContext
import maryk.core.properties.definitions.IsChangeableValueDefinition
import maryk.core.properties.definitions.IsComparableDefinition
import maryk.core.properties.definitions.IsSerializablePropertyDefinition
import maryk.core.properties.definitions.index.IsIndexable
import maryk.core.properties.definitions.index.Multiple
import maryk.core.properties.definitions.index.Reversed
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
fun convertFilterToIndexPartsToMatch(
    indexable: IsIndexable,
    keySize: Int,
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
            ) { index, _, byteArray ->
                val keyIndex = convertIndex?.invoke(index)
                listOfIndexParts.add(
                    IndexPartialToMatch(index, keyIndex, keySize, byteArray)
                )
            }
        }
        is GreaterThan -> walkFilterReferencesAndValues(filter, indexable) { index, keyDefinition, byteArray ->
            val keyIndex = convertIndex?.invoke(index)
            listOfIndexParts.add(
                indexPartialWithDirection(keyDefinition !is Reversed<*>, index, keyIndex, keySize, byteArray, false)
            )
        }
        is GreaterThanEquals -> walkFilterReferencesAndValues(filter, indexable) { index, keyDefinition, byteArray ->
            val keyIndex = convertIndex?.invoke(index)
            listOfIndexParts.add(
                indexPartialWithDirection(keyDefinition !is Reversed<*>, index, keyIndex, keySize, byteArray, true)
            )
        }
        is LessThan -> walkFilterReferencesAndValues(filter, indexable) { index, keyDefinition, byteArray ->
            val keyIndex = convertIndex?.invoke(index)
            listOfIndexParts.add(
                indexPartialWithDirection(keyDefinition is Reversed<*>, index, keyIndex, keySize, byteArray, false)
            )
        }
        is LessThanEquals -> walkFilterReferencesAndValues(filter, indexable) { index, keyDefinition, byteArray ->
            val keyIndex = convertIndex?.invoke(index)
            listOfIndexParts.add(
                indexPartialWithDirection(keyDefinition is Reversed<*>, index, keyIndex, keySize, byteArray, true)
            )
        }
        is Range -> for ((reference, value) in filter.referenceRangePairs) {
            getDefinitionOrNull(indexable, reference) { index, keyDefinition ->
                val keyIndex = convertIndex?.invoke(index)
                val fromBytes = convertValueToIndexableBytes(keyDefinition, value.from)
                val toBytes = convertValueToIndexableBytes(keyDefinition, value.to)
                listOfIndexParts.add(
                    indexPartialWithDirection(keyDefinition !is Reversed<*>, index, keyIndex, keySize, fromBytes, value.inclusiveFrom)
                )
                listOfIndexParts.add(
                    indexPartialWithDirection(keyDefinition is Reversed<*>, index, keyIndex, keySize, toBytes, value.inclusiveTo)
                )
            }
        }
        is ValueIn -> for ((reference, value) in filter.referenceValuePairs) {
            getDefinitionOrNull(indexable, reference) { index, keyDefinition ->
                val keyIndex = convertIndex?.invoke(index)
                val list = ArrayList<ByteArray>(value.size)
                for (setValue in value) {
                    list.add(
                        convertValueToIndexableBytes(keyDefinition, setValue)
                    )
                }
                list.sortWith(object : Comparator<ByteArray> {
                    override fun compare(a: ByteArray, b: ByteArray) = a.compareTo(b)
                })
                listOfIndexParts.add(
                    IndexPartialToBeOneOf(index, keyIndex, keySize, list)
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
                convertFilterToIndexPartsToMatch(indexable, keySize, convertIndex, aFilter, listOfIndexParts, listOfEqualPairs, listOfUniqueFilters)
            }
        }
        else -> {
            /** Skip since other filters are not supported for key scan ranges*/
        }
    }
}

private fun indexPartialWithDirection(
    bigger: Boolean,
    index: Int,
    keyIndex: Int?,
    keySize: Int,
    byteArray: ByteArray,
    inclusive: Boolean
): IsIndexPartialToMatch {
    return if (bigger) {
        IndexPartialToBeBigger(index, keyIndex, keySize, byteArray, inclusive)
    } else {
        IndexPartialToBeSmaller(index, keyIndex, keySize, byteArray, inclusive)
    }
}

/** Convert [value] with [indexableRef] into a key ByteArray */
private fun convertValueToIndexableBytes(
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
    handleKeyBytes: (Int, IsIndexablePropertyReference<Any>, ByteArray) -> Unit
) {
    for ((reference, value) in referenceValuePairs.referenceValuePairs) {
        getDefinitionOrNull(indexable, reference) { index, keyDefinition ->
            val byteArray = convertValueToIndexableBytes(keyDefinition, value)

            handleKeyBytes(index, keyDefinition, byteArray)
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
