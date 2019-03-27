package maryk.core.processors.datastore

import maryk.core.extensions.bytes.MAX_BYTE
import maryk.core.models.IsRootValuesDataModel
import maryk.core.query.filters.IsFilter
import maryk.core.query.pairs.ReferenceValuePair
import maryk.lib.extensions.compare.compareTo

/** Create a scan range by [filter] and [startKey] */
fun <DM : IsRootValuesDataModel<*>> DM.createScanRange(filter: IsFilter?, startKey: ByteArray?): KeyScanRange {
    val listOfKeyParts = mutableListOf<IsIndexPartialToMatch>()
    val listOfUniqueFilters = mutableListOf<UniqueToMatch>()
    val listOfEqualPairs = mutableListOf<ReferenceValuePair<Any>>()
    convertFilterToIndexPartsToMatch(this.keyDefinition, this.keyByteSize, { this.keyIndices[it] }, filter, listOfKeyParts, listOfEqualPairs, listOfUniqueFilters)

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
): KeyScanRange {
    val start = ArrayList<Byte>(this.keyByteSize)
    val end = ArrayList<Byte>(this.keyByteSize)

    var startKeyIndex = -1 // only highered on exact matches so breaks if too low
    var endKeyIndex = -1 // only highered on exact matches so breaks if too low
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

        when (keyPart) {
            is IndexPartialToMatch -> {
                if (startKeyIndex == keyIndex - 1 && startInclusive) startKeyIndex++
                if (endKeyIndex == keyIndex - 1 && endInclusive) endKeyIndex++

                keyPart.toMatch.forEach {
                    if (startKeyIndex == keyIndex) start += it
                    if (endKeyIndex == keyIndex) end += it
                }
                toRemove.add(keyPart)
            }
            is IndexPartialToBeBigger -> {
                if (startKeyIndex == keyIndex -1 && startInclusive) {
                    startKeyIndex++
                    keyPart.toBeSmaller.forEach {
                        start += it
                    }
                    startInclusive = keyPart.inclusive

                    toRemove.add(keyPart)
                }
            }
            is IndexPartialToBeSmaller -> {
                if (endKeyIndex == keyIndex - 1 && endInclusive) {
                    endKeyIndex++

                    keyPart.toBeBigger.forEach {
                        end += it
                    }
                    endInclusive = keyPart.inclusive

                    toRemove.add(keyPart)
                }
            }
            is IndexPartialToBeOneOf -> {
                if (startKeyIndex == keyIndex - 1 && startInclusive) {
                    startKeyIndex++
                    keyPart.toBeOneOf.first().forEach {
                        start += it
                    }
                }

                if (endKeyIndex == keyIndex - 1 && endInclusive) {
                    endKeyIndex++
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

    return KeyScanRange(
        start = if (startKey != null && startArray < startKey) startKey else startArray,
        startInclusive = startInclusive,
        end = end.toByteArray(),
        endInclusive = endInclusive,
        partialMatches = listOfParts,
        equalPairs = listOfEqualPairs,
        uniques = listOfUniqueFilters,
        keySize = this.keyByteSize
    )
}
