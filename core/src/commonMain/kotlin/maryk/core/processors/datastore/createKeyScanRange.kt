package maryk.core.processors.datastore

import maryk.core.models.IsRootValuesDataModel
import maryk.core.query.filters.IsFilter
import maryk.core.query.pairs.ReferenceValuePair
import maryk.lib.extensions.compare.compareTo

/** Create a scan range by [filter] and [startKey] */
fun <DM : IsRootValuesDataModel<*>> DM.createScanRange(filter: IsFilter?, startKey: ByteArray?): ScanRange {
    val listOfKeyParts = mutableListOf<IsIndexPartialToMatch>()
    val listOfUniqueFilters = mutableListOf<UniqueToMatch>()
    val listOfEqualPairs = mutableListOf<ReferenceValuePair<Any>>()
    convertFilterToKeyPartsToMatch(this.keyDefinition, { this.keyIndices[it] }, filter, listOfKeyParts, listOfEqualPairs, listOfUniqueFilters)

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
): ScanRange {
    val keySize = this.keyByteSize
    val start = ByteArray(keySize)
    val end = ByteArray(keySize) { -1 }
    var startInclusive = true
    var endInclusive = true

    var currentOffset: Int
    var keyIndex = -1
    val toRemove = mutableListOf<IsIndexPartialToMatch>()
    for (keyPart in listOfParts) {
        if (keyIndex + 1 == keyPart.indexableIndex) {
            keyIndex++ // continue to next indexable
        } else if (keyIndex != keyPart.indexableIndex) {
            break // Break loop since keyIndex is larger than expected or smaller which should never happen
        }

        currentOffset = keyIndices[keyIndex]

        when (keyPart) {
            is IndexPartialToMatch -> {
                keyPart.toMatch.forEachIndexed { i, b ->
                    start[i + currentOffset] = b
                    end[i + currentOffset] = b
                }
                val nextIndex = currentOffset + keyPart.toMatch.size
                // Separator to 1 so match is exact
                if (nextIndex < keySize) {
                    start[nextIndex] = 1
                    end[nextIndex] = 1
                }
                toRemove.add(keyPart)
            }
            is IndexPartialToBeBigger -> {
                keyPart.toBeSmaller.forEachIndexed { i, b ->
                    start[i + currentOffset] = b
                }
                val nextIndex = currentOffset + keyPart.toBeSmaller.size
                // Separator to 1 so match is exact
                if (nextIndex < keySize) {
                    start[nextIndex] = if (keyPart.inclusive) 1 else 2
                } else {
                    startInclusive = keyPart.inclusive
                }
                toRemove.add(keyPart)
            }
            is IndexPartialToBeSmaller -> {
                keyPart.toBeBigger.forEachIndexed { i, b ->
                    end[i + currentOffset] = b
                }
                val nextIndex = currentOffset + keyPart.toBeBigger.size
                // Separator to 1 so match is exact
                if (nextIndex < keySize) {
                    end[nextIndex] = if (keyPart.inclusive) 1 else 0
                } else {
                    endInclusive = keyPart.inclusive
                }
                toRemove.add(keyPart)
            }
            is IndexPartialToBeOneOf -> {
                val first = keyPart.toBeOneOf.first()
                first.forEachIndexed { i, b ->
                    start[i + currentOffset] = b
                }
                keyPart.toBeOneOf.last().forEachIndexed { i, b ->
                    end[i + currentOffset] = b
                }
                val nextIndex = currentOffset + first.size
                // Separator to 1 so match is exact
                if (nextIndex < keySize) {
                    start[nextIndex] = 1
                    end[nextIndex] = 1
                }
            }
        }
    }

    for (partToRemove in toRemove) {
        listOfParts.remove(partToRemove)
    }

    return ScanRange(
        start = if (startKey != null && start < startKey) startKey else start,
        startInclusive = startInclusive,
        end = end,
        endInclusive = endInclusive,
        equalPairs = listOfEqualPairs,
        uniques = listOfUniqueFilters,
        partialMatches = listOfParts
    )
}
