package maryk.core.processors.datastore.scanRange

import maryk.core.processors.datastore.matchers.IndexPartialSizeToMatch
import maryk.core.processors.datastore.matchers.IndexPartialToBeBigger
import maryk.core.processors.datastore.matchers.IndexPartialToBeOneOf
import maryk.core.processors.datastore.matchers.IndexPartialToBeSmaller
import maryk.core.processors.datastore.matchers.IndexPartialToMatch
import maryk.core.processors.datastore.matchers.IndexPartialToRegexMatch
import maryk.core.processors.datastore.matchers.IsIndexPartialToMatch
import maryk.core.properties.definitions.index.AnyOf
import maryk.core.processors.datastore.matchers.convertFilterToIndexPartsToMatch
import maryk.core.properties.definitions.index.IsIndexable
import maryk.core.query.filters.IsFilter
import maryk.lib.extensions.compare.nextByteInSameLength

/** Create a scan range with [filter] and [keyScanRange] */
fun IsIndexable.createScanRange(filter: IsFilter?, keyScanRange: KeyScanRanges, startIndexKey: ByteArray? = null): IndexableScanRanges {
    val listOfKeyParts = mutableListOf<IsIndexPartialToMatch>()
    convertFilterToIndexPartsToMatch(this, keyScanRange.keySize, null, filter, listOfKeyParts)
    listOfKeyParts.sortWith(compareBy<IsIndexPartialToMatch> { it.indexableIndex }.thenBy {
        when (it) {
            is IndexPartialToMatch -> 0
            is IndexPartialToBeOneOf -> 1
            else -> 2
        }
    })
    return createScanRangeFromParts(listOfKeyParts, keyScanRange, startIndexKey, this is AnyOf)
}

/**
 * Create scan range from [listOfParts]
 * It writes complete start and end keys with the partials to match
 */
private fun createScanRangeFromParts(
    listOfParts: MutableList<IsIndexPartialToMatch>,
    keyScanRange: KeyScanRanges,
    startIndexKey: ByteArray?,
    isAnyOf: Boolean
): IndexableScanRanges {
    val start = mutableListOf(mutableListOf<Byte>())
    val end = mutableListOf(mutableListOf<Byte>())

    var startShouldContinue = true
    var endShouldContinue = true

    var keyIndex = -1
    var rangePartIndex: Int? = null

    var startInclusive = true
    var endInclusive = true

    val anchoredIndexParts = mutableSetOf<Int>()
    val valueMatches = mutableListOf<IndexValueMatch>()
    val toAdd = mutableListOf<IsIndexPartialToMatch>()
    val toRemove = mutableListOf<IsIndexPartialToMatch>()

    for (keyPart in listOfParts) {
        if (keyPart.indexableIndex in anchoredIndexParts) {
            if (isAnyOf && keyPart is IndexPartialToMatch) {
                valueMatches += IndexValueMatch(keyPart.toMatch, keyPart.partialMatch)
                toRemove += keyPart
            }
            continue
        }
        // A range part ends the contiguous prefix. Later index fields remain
        // residual predicates; the opposite bound may still use this same part.
        if (rangePartIndex?.let { keyPart.indexableIndex > it } == true) break
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
                val appliedToRange = startShouldContinue || endShouldContinue
                keyPart.toMatch.forEach { byte ->
                    if (startShouldContinue) addByte(start, byte)
                    if (endShouldContinue) addByte(end, byte)
                }

                // Partial matches can only happen on index since is string only
                if (!keyPart.partialMatch) {
                    // Add size checker for exact matches
                    toAdd += IndexPartialSizeToMatch(
                        keyIndex,
                        null,
                        keyPart.keySize,
                        keyPart.indexPartCount,
                        keyPart.toMatch.size
                    )
                }

                if (keyPart.partialMatch && appliedToRange) {
                    // Ensure no more parts are added with partial match by invalidating the keyIndex
                    startShouldContinue = false
                    endShouldContinue = false
                }

                if (appliedToRange) {
                    anchoredIndexParts += keyPart.indexableIndex
                    if (!keyPart.partialMatch) {
                        toRemove += keyPart
                    }
                }
            }
            is IndexPartialToBeBigger -> {
                rangePartIndex = keyPart.indexableIndex
                if (startShouldContinue) {
                    keyPart.toBeSmaller.forEach { addByte(start, it) }
                    // Start at the prefix itself, then enforce strictness on the
                    // actual part. Advancing a string prefix would skip longer values.
                    startInclusive = true
                    startShouldContinue = false
                }
            }
            is IndexPartialToBeSmaller -> {
                rangePartIndex = keyPart.indexableIndex
                if (endShouldContinue) {
                    keyPart.toBeBigger.forEach { addByte(end, it) }
                    endInclusive = keyPart.inclusive
                    endShouldContinue = false
                    if (keyPart.inclusive) {
                        // Include all trailing index fields at this boundary.
                        // The residual matcher rejects longer variable-size values.
                        end.forEach { bytes ->
                            val bound = bytes.toByteArray()
                            val successor = bound.nextByteInSameLength()
                            bytes.clear()
                            if (successor !== bound) bytes.addAll(successor.toList())
                        }
                        endInclusive = false
                    }
                }
            }
            is IndexPartialToBeOneOf -> {
                anchoredIndexParts += keyPart.indexableIndex
                if (keyPart.toBeOneOf.isEmpty()) {
                    return IndexableScanRanges(
                        ranges = emptyList(),
                        partialMatches = listOfParts,
                        keyScanRange = keyScanRange,
                        valueMatches = valueMatches
                    )
                }
                val startSizeBefore = start.size
                val endSizeBefore = end.size
                multiplyList(start, end, keyPart.toBeOneOf.size)
                keyPart.toBeOneOf.forEachIndexed { oneOfIndex, oneOfBytes ->
                    oneOfBytes.forEach { oneOfByte ->
                        if (startShouldContinue) {
                            repeat(startSizeBefore) { start[oneOfIndex * startSizeBefore + it] += oneOfByte }
                        }
                        if (endShouldContinue) {
                            repeat(endSizeBefore) { end[oneOfIndex * endSizeBefore + it] += oneOfByte }
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

    // A lower bound alone leaves the equality prefix as the upper boundary.
    // Include its complete suffix, rather than comparing the prefix to full rows.
    if (rangePartIndex != null && endShouldContinue && end.any { it.isNotEmpty() }) {
        end.forEach { bytes ->
            val bound = bytes.toByteArray()
            val successor = bound.nextByteInSameLength()
            bytes.clear()
            if (successor !== bound) bytes.addAll(successor.toList())
        }
        endInclusive = false
    }

    listOfParts.apply {
        removeAll(toRemove)
        addAll(toAdd)
    }

    return IndexableScanRanges(
        ranges = createRanges(start, end, startInclusive, endInclusive, startIndexKey),
        partialMatches = listOfParts,
        keyScanRange = keyScanRange,
        valueMatches = valueMatches
    )
}
