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

    var startKeyIndex = 0
    var endKeyIndex = 0
    var keyIndex = -1
    val toRemove = mutableListOf<IsIndexPartialToMatch>()
    for (keyPart in listOfParts) {
        if (keyIndex + 1 == keyPart.indexableIndex) {
            keyIndex++ // continue to next indexable
        } else if (keyIndex != keyPart.indexableIndex) {
            break // Break loop since keyIndex is larger than expected or smaller which should never happen
        }

        when (keyPart) {
            is IndexPartialToMatch -> {
                if (startKeyIndex == keyIndex - 1) startKeyIndex++
                if (endKeyIndex == keyIndex - 1) endKeyIndex++

                keyPart.toMatch.forEach {
                    if (startKeyIndex == keyIndex) start += it
                    if (endKeyIndex == keyIndex) end += it
                }
                // Separator to 1 so match is exact
                if (startKeyIndex == keyIndex) start += 1
                if (endKeyIndex == keyIndex) end += 1
                toRemove.add(keyPart)
            }
            is IndexPartialToBeBigger -> {
                if (startKeyIndex == keyIndex - 1) startKeyIndex++

                if (startKeyIndex == keyIndex) {
                    keyPart.toBeSmaller.forEach {
                        start += it
                    }
                    // Separator to 1 so match is exact
                    start += if (keyPart.inclusive) 1.toByte() else 2.toByte()
                }
                toRemove.add(keyPart)
            }
            is IndexPartialToBeSmaller -> {
                if (endKeyIndex == keyIndex - 1) endKeyIndex++
                if (endKeyIndex == keyIndex) {
                    keyPart.toBeBigger.forEach {
                        end += it
                    }
                    // Separator to 1 so match is exact
                    end += if (keyPart.inclusive) 1.toByte() else 0.toByte()
                    toRemove.add(keyPart)
                }
            }
            is IndexPartialToBeOneOf -> {
                if (startKeyIndex == keyIndex - 1) startKeyIndex++
                if (endKeyIndex == keyIndex - 1) endKeyIndex++

                if (startKeyIndex == keyIndex) {
                    keyPart.toBeOneOf.first().forEach {
                        start += it
                    }
                }

                if (endKeyIndex == keyIndex) {
                    keyPart.toBeOneOf.last().forEach {
                        end += it
                    }
                }
                // Separator to 1 so match is exact
                if (startKeyIndex == keyIndex) start += 1
                if (endKeyIndex == keyIndex) end += 1
            }
        }
    }

    for (partToRemove in toRemove) {
        listOfParts.remove(partToRemove)
    }

    return IndexableScanRange(
        start = start.toByteArray(),
        startInclusive = true,
        end = end.toByteArray(),
        endInclusive = false,
        partialMatches = listOfParts,
        keyScanRange = keyScanRange
    )
}
