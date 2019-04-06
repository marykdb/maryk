package maryk.core.processors.datastore.scanRange

import maryk.core.extensions.bytes.MAX_BYTE
import maryk.core.models.IsRootValuesDataModel
import maryk.core.processors.datastore.matchers.IndexPartialToBeBigger
import maryk.core.processors.datastore.matchers.IndexPartialToBeOneOf
import maryk.core.processors.datastore.matchers.IndexPartialToBeSmaller
import maryk.core.processors.datastore.matchers.IndexPartialToMatch
import maryk.core.processors.datastore.matchers.IsIndexPartialToMatch
import maryk.core.processors.datastore.matchers.UniqueToMatch
import maryk.core.processors.datastore.matchers.convertFilterToIndexPartsToMatch
import maryk.core.query.filters.IsFilter
import maryk.core.query.pairs.ReferenceValuePair
import maryk.lib.extensions.compare.compareTo

/** Create a scan range by [filter] and [startKey] */
fun <DM : IsRootValuesDataModel<*>> DM.createScanRange(filter: IsFilter?, startKey: ByteArray?): KeyScanRanges {
    val listOfKeyParts = mutableListOf<IsIndexPartialToMatch>()
    val listOfUniqueFilters = mutableListOf<UniqueToMatch>()
    val listOfEqualPairs = mutableListOf<ReferenceValuePair<Any>>()
    convertFilterToIndexPartsToMatch(
        this.keyDefinition,
        this.keyByteSize,
        { this.keyIndices[it] },
        filter,
        listOfKeyParts,
        listOfEqualPairs,
        listOfUniqueFilters
    )

    listOfKeyParts.sortBy { it.fromByteIndex }

    return createScanRangeFromParts(startKey, listOfKeyParts, listOfEqualPairs, listOfUniqueFilters)
}

/**
 * Create scan range from [listOfParts] and check with [startKey]
 * It writes complete start and end keys with the partials to match
 */
private fun <DM : IsRootValuesDataModel<*>> DM.createScanRangeFromParts(
    startKey: ByteArray?,
    listOfParts: MutableList<IsIndexPartialToMatch>,
    listOfEqualPairs: List<ReferenceValuePair<Any>>,
    listOfUniqueFilters: List<UniqueToMatch>
): KeyScanRanges {
    val start = ArrayList<Byte>(this.keyByteSize)
    val end = ArrayList<Byte>(this.keyByteSize)

    var startShouldContinue = true
    var endShouldContinue = true

    var keyIndex = -1

    var startInclusive = true
    var endInclusive = true

    val toRemove = mutableListOf<IsIndexPartialToMatch>()
    for (keyPart in listOfParts) {
        if (keyIndex + 1 == keyPart.indexableIndex) {
            keyIndex++ // continue to next indexable
        } else if (keyIndex != keyPart.indexableIndex) {
            break // Break loop since keyIndex is larger than expected or smaller which should never happen
        } else if (!startInclusive && !endInclusive) {
            break // Break loop finding parts since no inclusive scan parts are possible
        }

        startShouldContinue = startShouldContinue && startInclusive
        endShouldContinue = endShouldContinue && endInclusive

        when (keyPart) {
            is IndexPartialToMatch -> {
                keyPart.toMatch.forEach {
                    if (startShouldContinue) start += it
                    if (endShouldContinue) end += it
                }
                toRemove.add(keyPart)
            }
            is IndexPartialToBeBigger -> {
                if (startShouldContinue) {
                    keyPart.toBeSmaller.forEach {
                        start += it
                    }
                    startInclusive = keyPart.inclusive

                    toRemove.add(keyPart)
                }
            }
            is IndexPartialToBeSmaller -> {
                if (endShouldContinue) {
                    keyPart.toBeBigger.forEach {
                        end += it
                    }
                    endInclusive = keyPart.inclusive

                    toRemove.add(keyPart)
                }
            }
            is IndexPartialToBeOneOf -> {
                if (startShouldContinue) {
                    keyPart.toBeOneOf.first().forEach {
                        start += it
                    }
                }
                if (endShouldContinue) {
                    keyPart.toBeOneOf.last().forEach {
                        end += it
                    }
                }
            }
        }
    }

    for (partToRemove in toRemove) {
        listOfParts.remove(partToRemove)
    }

    // Fill start key with MAX bytes so it properly goes to the end
    for (it in 1..(this.keyByteSize - start.size)) {
        start += if (startInclusive) 0 else MAX_BYTE
    }

    // Fill end key with MAX bytes so it properly goes to the end
    for (it in 1..(this.keyByteSize - end.size)) {
        end += if (endInclusive) MAX_BYTE else 0
    }

    val startArray = start.toByteArray()

    val scanRange = ScanRange(
        start = if (startKey != null && startArray < startKey) startKey else startArray,
        startInclusive = startInclusive,
        end = end.toByteArray(),
        endInclusive = endInclusive
    )

    return KeyScanRanges(
        ranges = listOf(scanRange),
        partialMatches = listOfParts,
        equalPairs = listOfEqualPairs,
        uniques = listOfUniqueFilters,
        keySize = this.keyByteSize
    )
}
