package maryk.core.processors.datastore.matchers

import maryk.core.exceptions.TypeException
import maryk.core.properties.IsPropertyContext
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
import maryk.core.query.filters.Prefix
import maryk.core.query.filters.Range
import maryk.core.query.filters.RegEx
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
        null -> return
        is Equals -> handleEquals(filter, indexable, convertIndex, keySize, listOfIndexParts, listOfEqualPairs, listOfUniqueFilters)
        is Prefix -> handlePrefix(filter, indexable, convertIndex, keySize, listOfIndexParts, listOfUniqueFilters)
        is GreaterThan -> handleComparison(filter, indexable, convertIndex, keySize, listOfIndexParts, false, false)
        is GreaterThanEquals -> handleComparison(filter, indexable, convertIndex, keySize, listOfIndexParts, false, true)
        is LessThan -> handleComparison(filter, indexable, convertIndex, keySize, listOfIndexParts, true, false)
        is LessThanEquals -> handleComparison(filter, indexable, convertIndex, keySize, listOfIndexParts, true, true)
        is Range -> handleRange(filter, indexable, convertIndex, keySize, listOfIndexParts)
        is ValueIn -> handleValueIn(filter, indexable, convertIndex, keySize, listOfIndexParts, listOfUniqueFilters)
        is RegEx -> handleRegEx(filter, indexable, keySize, listOfIndexParts)
        is And -> filter.filters.forEach { subFilter ->
            convertFilterToIndexPartsToMatch(indexable, keySize, convertIndex, subFilter, listOfIndexParts, listOfEqualPairs, listOfUniqueFilters)
        }
        else -> { /* Skip unsupported filters */ }
    }
}

private fun handleEquals(
    filter: Equals,
    indexable: IsIndexable,
    convertIndex: ((Int) -> Int)?,
    keySize: Int,
    listOfIndexParts: MutableList<IsIndexPartialToMatch>,
    listOfEqualPairs: MutableList<ReferenceValuePair<Any>>?,
    listOfUniqueFilters: MutableList<UniqueToMatch>?
) {
    listOfEqualPairs?.addAll(filter.referenceValuePairs)
    walkFilterReferencesAndValues(filter, indexable, listOfUniqueFilters) { index, _, byteArray ->
        val keyIndex = convertIndex?.invoke(index)
        listOfIndexParts.add(IndexPartialToMatch(index, keyIndex, keySize, byteArray))
    }
}

private fun handlePrefix(
    filter: Prefix,
    indexable: IsIndexable,
    convertIndex: ((Int) -> Int)?,
    keySize: Int,
    listOfIndexParts: MutableList<IsIndexPartialToMatch>,
    listOfUniqueFilters: MutableList<UniqueToMatch>?
) {
    walkFilterReferencesAndValues(filter, indexable, listOfUniqueFilters) { index, _, byteArray ->
        val keyIndex = convertIndex?.invoke(index)
        listOfIndexParts.add(IndexPartialToMatch(index, keyIndex, keySize, byteArray, partialMatch = true))
    }
}

private fun handleComparison(
    filter: IsReferenceValuePairsFilter<*>,
    indexable: IsIndexable,
    convertIndex: ((Int) -> Int)?,
    keySize: Int,
    listOfIndexParts: MutableList<IsIndexPartialToMatch>,
    isReversed: Boolean,
    isInclusive: Boolean
) {
    walkFilterReferencesAndValues(filter, indexable) { index, keyDefinition, byteArray ->
        val keyIndex = convertIndex?.invoke(index)
        listOfIndexParts.add(
            indexPartialWithDirection(
                (keyDefinition !is Reversed<*>) xor isReversed,
                index,
                keyIndex,
                keySize,
                byteArray,
                isInclusive
            )
        )
    }
}

private fun handleRange(
    filter: Range,
    indexable: IsIndexable,
    convertIndex: ((Int) -> Int)?,
    keySize: Int,
    listOfIndexParts: MutableList<IsIndexPartialToMatch>
) {
    for ((reference, value) in filter.referenceValuePairs) {
        getDefinitionOrNull(indexable, reference) { index, keyDefinition ->
            val keyIndex = convertIndex?.invoke(index)
            val fromBytes = convertValueToIndexableBytes(keyDefinition, value.from)
            val toBytes = convertValueToIndexableBytes(keyDefinition, value.to)
            listOfIndexParts.add(
                indexPartialWithDirection(
                    keyDefinition !is Reversed<*>,
                    index,
                    keyIndex,
                    keySize,
                    fromBytes,
                    value.inclusiveFrom
                )
            )
            listOfIndexParts.add(
                indexPartialWithDirection(
                    keyDefinition is Reversed<*>,
                    index,
                    keyIndex,
                    keySize,
                    toBytes,
                    value.inclusiveTo
                )
            )
        }
    }
}

private fun handleValueIn(
    filter: ValueIn,
    indexable: IsIndexable,
    convertIndex: ((Int) -> Int)?,
    keySize: Int,
    listOfIndexParts: MutableList<IsIndexPartialToMatch>,
    listOfUniqueFilters: MutableList<UniqueToMatch>?
) {
    for ((reference, value) in filter.referenceValuePairs) {
        getDefinitionOrNull(indexable, reference) { index, keyDefinition ->
            val keyIndex = convertIndex?.invoke(index)
            val list = value.map { convertValueToIndexableBytes(keyDefinition, it) }.sortedWith { a, b -> a compareTo b }
            listOfIndexParts.add(IndexPartialToBeOneOf(index, keyIndex, keySize, list))
        }

        listOfUniqueFilters?.let {
            reference.comparablePropertyDefinition.let {
                if (it is IsComparableDefinition<*, *> && it.unique) {
                    value.forEach { uniqueToMatch ->
                        listOfUniqueFilters.add(createUniqueToMatch(reference, it, uniqueToMatch))
                    }
                }
            }
        }
    }
}

private fun handleRegEx(
    filter: RegEx,
    indexable: IsIndexable,
    keySize: Int,
    listOfIndexParts: MutableList<IsIndexPartialToMatch>
) {
    for ((reference, regex) in filter.referenceValuePairs) {
        getDefinitionOrNull(indexable, reference) { index, _ ->
            listOfIndexParts += IndexPartialToRegexMatch(index, keySize, regex)
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
): IsIndexPartialToMatch = if (bigger) {
    IndexPartialToBeBigger(index, keyIndex, keySize, byteArray, inclusive)
} else {
    IndexPartialToBeSmaller(index, keyIndex, keySize, byteArray, inclusive)
}

private fun convertValueToIndexableBytes(
    indexableRef: IsIndexablePropertyReference<Any>,
    value: Any
): ByteArray = ByteArray(indexableRef.calculateStorageByteLength(value)).also { byteArray ->
    var byteReadIndex = 0
    indexableRef.writeStorageBytes(value) { byteArray[byteReadIndex++] = it }
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
            handleKeyBytes(index, keyDefinition, convertValueToIndexableBytes(keyDefinition, value))
        }

        listOfUniqueFilters?.let {
            reference.comparablePropertyDefinition.let {
                if (it is IsComparableDefinition<*, *> && it.unique) {
                    listOfUniqueFilters.add(createUniqueToMatch(reference, it, value))
                }
            }
        }
    }
}

@Suppress("UNCHECKED_CAST")
private fun <T : Any> createUniqueToMatch(
    reference: IsPropertyReference<out T, IsSerializablePropertyDefinition<out T, IsPropertyContext>, *>,
    it: IsSerializablePropertyDefinition<out T, IsPropertyContext>,
    value: T
) = UniqueToMatch(
    reference.toStorageByteArray(),
    it as IsComparableDefinition<out Comparable<Any>, IsPropertyContext>,
    value as Comparable<*>
)

/** Get key definition by [reference] and [processKeyDefinitionWhenFound] using [indexable] or null if not part of key */
private fun <T : Any> getDefinitionOrNull(
    indexable: IsIndexable,
    reference: IsPropertyReference<out T, IsSerializablePropertyDefinition<out T, IsPropertyContext>, *>,
    processKeyDefinitionWhenFound: (Int, IsIndexablePropertyReference<Any>) -> Unit
) {
    when (indexable) {
        is Multiple -> indexable.references.withIndex()
            .firstOrNull { (_, keyDef) -> keyDef.isForPropertyReference(reference) }
            ?.let { (index, keyDef) ->
                @Suppress("UNCHECKED_CAST")
                processKeyDefinitionWhenFound(index, keyDef as IsIndexablePropertyReference<Any>)
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
