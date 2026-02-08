package maryk.core.processors.datastore.scanRange

import maryk.core.processors.datastore.matchers.IndexPartialSizeToMatch
import maryk.core.processors.datastore.matchers.IndexPartialToBeBigger
import maryk.core.processors.datastore.matchers.IndexPartialToBeOneOf
import maryk.core.processors.datastore.matchers.IndexPartialToBeSmaller
import maryk.core.processors.datastore.matchers.IndexPartialToMatch
import maryk.core.processors.datastore.matchers.IndexPartialToRegexMatch
import maryk.core.processors.datastore.matchers.IsIndexPartialToMatch
import maryk.core.processors.datastore.matchers.convertFilterToIndexPartsToMatch
import maryk.core.properties.definitions.index.IsIndexable
import maryk.core.query.filters.IsFilter

/** Create a scan range with [filter] and [keyScanRange] */
fun IsIndexable.createScanRange(filter: IsFilter?, keyScanRange: KeyScanRanges, startIndexKey: ByteArray? = null): IndexableScanRanges {
    val listOfKeyParts = mutableListOf<IsIndexPartialToMatch>()
    convertFilterToIndexPartsToMatch(this, keyScanRange.keySize, null, filter, listOfKeyParts)
    listOfKeyParts.sortBy { it.indexableIndex }
    return createScanRangeFromParts(listOfKeyParts, keyScanRange, startIndexKey)
}

/**
 * Create scan range from [listOfParts]
 * It writes complete start and end keys with the partials to match
 */
private fun createScanRangeFromParts(
    listOfParts: MutableList<IsIndexPartialToMatch>,
    keyScanRange: KeyScanRanges,
    startIndexKey: ByteArray?
): IndexableScanRanges {
    val start = mutableListOf(mutableListOf<Byte>())
    val end = mutableListOf(mutableListOf<Byte>())

    var startShouldContinue = true
    var endShouldContinue = true

    var keyIndex = -1

    var startInclusive = true
    var endInclusive = true

    val toAdd = mutableListOf<IsIndexPartialToMatch>()
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
                keyPart.toMatch.forEach { byte ->
                    if (startShouldContinue) addByte(start, byte)
                    if (endShouldContinue) addByte(end, byte)
                }

                // Partial matches can only happen on index since is string only
                if (!keyPart.partialMatch) {
                    // Add size checker for exact matches
                    toAdd += IndexPartialSizeToMatch(keyIndex, null, keyPart.keySize, keyPart.toMatch.size)
                } else {
                    // Ensure no more parts are added with partial match by invalidating the keyIndex
                    startShouldContinue = false
                    endShouldContinue = false
                }
                toRemove += keyPart
            }
            is IndexPartialToBeBigger -> {
                if (startShouldContinue) {
                    keyPart.toBeSmaller.forEach { addByte(start, it) }
                    startInclusive = keyPart.inclusive
                    toRemove += keyPart
                }
            }
            is IndexPartialToBeSmaller -> {
                if (endShouldContinue) {
                    keyPart.toBeBigger.forEach { addByte(end, it) }
                    endInclusive = keyPart.inclusive
                    toRemove += keyPart
                }
            }
            is IndexPartialToBeOneOf -> {
                if (keyPart.toBeOneOf.isEmpty()) {
                    return IndexableScanRanges(
                        ranges = emptyList(),
                        partialMatches = listOfParts,
                        keyScanRange = keyScanRange
                    )
                }
                val startSizeBefore = start.size
                val endSizeBefore = end.size
                multiplyList(start, end, keyPart.toBeOneOf.size)
                keyPart.toBeOneOf.forEachIndexed { oneOfIndex, oneOfBytes ->
                    oneOfBytes.forEach { oneOfByte ->
                        if (startShouldContinue) {
                            repeat(startSizeBefore) { start[it + oneOfIndex] += oneOfByte }
                        }
                        if (endShouldContinue) {
                            repeat(endSizeBefore) { end[it + oneOfIndex] += oneOfByte }
                        }
                    }
                }
            }
            is IndexPartialToRegexMatch -> {
                // Cannot create a scan range with a Regular Expression so ignore
            }
            is IndexPartialSizeToMatch -> {
                // Partial sizes are not used for scan range creation
            }
        }
    }

    listOfParts.apply {
        removeAll(toRemove)
        addAll(toAdd)
    }

    return IndexableScanRanges(
        ranges = createRanges(start, end, startInclusive, endInclusive, startIndexKey),
        partialMatches = listOfParts,
        keyScanRange = keyScanRange
    )
}
