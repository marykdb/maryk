package maryk.core.processors.datastore

import maryk.core.properties.definitions.index.IsIndexable
import maryk.core.query.filters.IsFilter

/** Create a scan range with [filter] and [keyScanRange] */
fun IsIndexable.createScanRange(filter: IsFilter?, keyScanRange: KeyScanRange): IndexableScanRange {
    val listOfKeyParts = mutableListOf<IsIndexPartialToMatch>()
    convertFilterToIndexPartsToMatch(this, keyScanRange.keySize,null, filter, listOfKeyParts)

    listOfKeyParts.sortBy { it.fromByteIndex }

    return createScanRangeFromParts(listOfKeyParts, keyScanRange)
}

/**
 * Create scan range from [listOfParts]
 * It writes complete start and end keys with the partials to match
 */
private fun createScanRangeFromParts(
    listOfParts: MutableList<IsIndexPartialToMatch>,
    keyScanRange: KeyScanRange
): IndexableScanRange {
    val start = mutableListOf<Byte>()
    val end = mutableListOf<Byte>()

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

    return IndexableScanRange(
        start = start.toByteArray(),
        startInclusive = startInclusive,
        end = end.toByteArray(),
        endInclusive = endInclusive,
        partialMatches = listOfParts,
        keyScanRange = keyScanRange
    )
}
