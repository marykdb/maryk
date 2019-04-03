package maryk.core.processors.datastore.scanRange

import maryk.core.processors.datastore.matchers.IndexPartialSizeToMatch
import maryk.core.processors.datastore.matchers.IndexPartialToBeBigger
import maryk.core.processors.datastore.matchers.IndexPartialToBeOneOf
import maryk.core.processors.datastore.matchers.IndexPartialToBeSmaller
import maryk.core.processors.datastore.matchers.IndexPartialToMatch
import maryk.core.processors.datastore.matchers.IsIndexPartialToMatch
import maryk.core.processors.datastore.matchers.convertFilterToIndexPartsToMatch
import maryk.core.properties.definitions.index.IsIndexable
import maryk.core.query.filters.IsFilter

/** Create a scan range with [filter] and [keyScanRange] */
fun IsIndexable.createScanRange(filter: IsFilter?, keyScanRange: KeyScanRanges): IndexableScanRanges {
    val listOfKeyParts = mutableListOf<IsIndexPartialToMatch>()
    convertFilterToIndexPartsToMatch(
        this,
        keyScanRange.keySize,
        null,
        filter,
        listOfKeyParts
    )

    listOfKeyParts.sortBy { it.fromByteIndex }

    return createScanRangeFromParts(listOfKeyParts, keyScanRange)
}

/**
 * Create scan range from [listOfParts]
 * It writes complete start and end keys with the partials to match
 */
private fun createScanRangeFromParts(
    listOfParts: MutableList<IsIndexPartialToMatch>,
    keyScanRange: KeyScanRanges
): IndexableScanRanges {
    val start = mutableListOf<Byte>()
    val end = mutableListOf<Byte>()

    var startKeyIndex = -1 // only increased on exact matches so breaks if too low
    var endKeyIndex = -1 // only increased on exact matches so breaks if too low
    
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

        when (keyPart) {
            is IndexPartialToMatch -> {
                if (startKeyIndex == keyIndex - 1 && startInclusive) startKeyIndex++
                if (endKeyIndex == keyIndex - 1 && endInclusive) endKeyIndex++

                keyPart.toMatch.forEach {
                    if (startKeyIndex == keyIndex) start += it
                    if (endKeyIndex == keyIndex) end += it
                }

                if (!keyPart.partialMatch) {
                    // Add size checker for exact matches
                    toAdd.add(
                        IndexPartialSizeToMatch(
                            keyIndex,
                            null,
                            keyPart.keySize,
                            keyPart.toMatch.size
                        )
                    )
                } else {
                    // Ensure no more parts are added with partial match by invalidating the keyIndex
                    if (startKeyIndex == keyIndex) startKeyIndex = -1
                    if (endKeyIndex == keyIndex) endKeyIndex = -1
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

    listOfParts.removeAll(toRemove)
    listOfParts.addAll(toAdd)

    val range = ScanRange(
        start = start.toByteArray(),
        startInclusive = startInclusive,
        end = end.toByteArray(),
        endInclusive = endInclusive
    )

    return IndexableScanRanges(
        ranges = listOf(range),
        partialMatches = listOfParts,
        keyScanRange = keyScanRange
    )
}
