package maryk.core.processors.datastore.scanRange

import maryk.core.extensions.bytes.MAX_BYTE
import maryk.core.models.IsRootValuesDataModel
import maryk.core.processors.datastore.matchers.IndexPartialSizeToMatch
import maryk.core.processors.datastore.matchers.IndexPartialToBeBigger
import maryk.core.processors.datastore.matchers.IndexPartialToBeOneOf
import maryk.core.processors.datastore.matchers.IndexPartialToBeSmaller
import maryk.core.processors.datastore.matchers.IndexPartialToMatch
import maryk.core.processors.datastore.matchers.IndexPartialToRegexMatch
import maryk.core.processors.datastore.matchers.IsIndexPartialToMatch
import maryk.core.processors.datastore.matchers.UniqueToMatch
import maryk.core.processors.datastore.matchers.convertFilterToIndexPartsToMatch
import maryk.core.query.filters.IsFilter
import maryk.core.query.pairs.ReferenceValuePair
import maryk.lib.extensions.compare.nextByteInSameLength

/** Create a scan range by [filter] and [startKey] */
fun <DM : IsRootValuesDataModel<*>> DM.createScanRange(filter: IsFilter?, startKey: ByteArray?, includeStart: Boolean = true): KeyScanRanges {
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

    val properStartKey = if (includeStart) startKey else startKey?.nextByteInSameLength()

    return createScanRangeFromParts(properStartKey, listOfKeyParts, listOfEqualPairs, listOfUniqueFilters)
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
    val start: MutableList<MutableList<Byte>> = mutableListOf(ArrayList(this.keyByteSize))
    val end: MutableList<MutableList<Byte>> = mutableListOf(ArrayList(this.keyByteSize))

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
                    if (startShouldContinue) addByte(start, it)
                    if (endShouldContinue) addByte(end, it)
                }
                toRemove.add(keyPart)
            }
            is IndexPartialToBeBigger -> {
                if (startShouldContinue) {
                    keyPart.toBeSmaller.forEach {
                        addByte(start, it)
                    }
                    startInclusive = keyPart.inclusive

                    toRemove.add(keyPart)
                }
            }
            is IndexPartialToBeSmaller -> {
                if (endShouldContinue) {
                    keyPart.toBeBigger.forEach {
                        addByte(end, it)
                    }
                    endInclusive = keyPart.inclusive

                    toRemove.add(keyPart)
                }
            }
            is IndexPartialToBeOneOf -> {
                val startSizeBefore = start.size
                val endSizeBefore = end.size

                multiplyList(start, end, keyPart.toBeOneOf.size)

                for ((oneOfIndex, oneOfBytes) in keyPart.toBeOneOf.withIndex()) {
                    oneOfBytes.forEach { oneOfByte ->
                        if (startShouldContinue) {
                            for (copiesIndex in (0 until startSizeBefore)) {
                                start[copiesIndex + oneOfIndex].add(oneOfByte)
                            }
                        }

                        if (endShouldContinue) {
                            for (copiesIndex in (0 until endSizeBefore)) {
                                end[copiesIndex + oneOfIndex].add(oneOfByte)
                            }
                        }
                    }
                }
            }
            is IndexPartialToRegexMatch -> {
                // Only used in indices since String is not possible in Key
            }
            is IndexPartialSizeToMatch -> {
                // Only used in indices since key cannot hold flexible size key parts
            }
        }
    }

    for (partToRemove in toRemove) {
        listOfParts.remove(partToRemove)
    }

    // Fill start key with MAX bytes so it properly goes to the end
    for (it in 1..(this.keyByteSize - start.first().size)) {
        addByte(start, if (startInclusive) 0 else MAX_BYTE)
    }

    // Fill end key with MAX bytes so it properly goes to the end
    for (it in 1..(this.keyByteSize - end.first().size)) {
        addByte(end, if (endInclusive) MAX_BYTE else 0)
    }

    return KeyScanRanges(
        ranges = createRanges(start, end, startInclusive, endInclusive, startKey),
        partialMatches = listOfParts,
        equalPairs = listOfEqualPairs,
        uniques = listOfUniqueFilters,
        keySize = this.keyByteSize
    )
}
