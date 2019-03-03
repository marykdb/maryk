package maryk.core.processors.datastore

import maryk.core.models.IsRootValuesDataModel
import maryk.core.properties.definitions.index.Multiple
import maryk.core.query.filters.IsFilter
import maryk.core.query.pairs.ReferenceValuePair
import maryk.lib.extensions.compare.compareTo

/** Create a scan range by [filter] and [startKey] */
fun <DM : IsRootValuesDataModel<*>> DM.createScanRange(filter: IsFilter?, startKey: ByteArray?): ScanRange {
    val listOfKeyParts = mutableListOf<IsKeyPartialToMatch>()
    val listOfUniqueFilters = mutableListOf<UniqueToMatch>()
    val listOfEqualPairs = mutableListOf<ReferenceValuePair<Any>>()
    convertFilterToKeyPartsToMatch(this, filter, listOfKeyParts, listOfEqualPairs, listOfUniqueFilters)

    listOfKeyParts.sortBy { it.fromIndex }

    return this.createScanRangeFromParts(startKey, listOfKeyParts, listOfEqualPairs, listOfUniqueFilters)
}

/** Create scan range from [listOfParts] and check with [startKey]  */
private fun <DM : IsRootValuesDataModel<*>> DM.createScanRangeFromParts(
    startKey: ByteArray?,
    listOfParts: MutableList<IsKeyPartialToMatch>,
    listOfEqualPairs: List<ReferenceValuePair<Any>>,
    listOfUniqueFilters: List<UniqueToMatch>
): ScanRange {
    val keySize = this.keyByteSize
    val start = ByteArray(keySize)
    val end = ByteArray(keySize) { -1 }
    var startInclusive = true
    var endInclusive = true

    var partCounter = 0
    var currentOffset = 0
    var keyIndex = 0
    val toRemove = mutableListOf<IsKeyPartialToMatch>()
    for (keyPart in listOfParts) {
        // If current key part bytes offset is lower than current key part and that part was already written (partCounter for keyIndex was there)
        if (currentOffset < keyPart.fromIndex && partCounter == keyIndex + 1) {
            currentOffset = (this.keyDefinition as? Multiple)?.let {
                if (keyIndices.size > 1) keyIndices[++keyIndex] else keySize
            } ?: keySize
        }
        // Break off if current offset is not expected for key part.
        if (currentOffset != keyPart.fromIndex) {
            break
        }

        partCounter++

        when (keyPart) {
            is KeyPartialToMatch -> {
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
            is KeyPartialToBeBigger -> {
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
            is KeyPartialToBeSmaller -> {
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
            is KeyPartialToBeOneOf -> {
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
