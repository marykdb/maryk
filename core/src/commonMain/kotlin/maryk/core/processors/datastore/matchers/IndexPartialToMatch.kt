package maryk.core.processors.datastore.matchers

import maryk.core.processors.datastore.findByteIndexAndSizeByPartIndex
import maryk.core.processors.datastore.findByteIndexByPartIndex
import maryk.lib.exceptions.ParseException
import maryk.lib.extensions.compare.compareToRange
import maryk.lib.extensions.compare.matchesRange
import maryk.lib.extensions.compare.matchesRangePart

sealed interface IsIndexPartialToMatch {
    val indexableIndex: Int
    val fromByteIndex: Int?
    val keySize: Int
    val indexPartCount: Int

    /**
     * Returns byte index to start reading for [bytes]
     * If this partial is for key bytes it already has the value or otherwise is for flex key then will calculate value
     */
    fun getByteIndex(bytes: ByteArray, sourceStart: Int = 0, sourceEnd: Int = bytes.size) =
        getByteIndexAndSize(bytes, sourceStart, sourceEnd)?.first ?: fromByteIndex ?: throw ParseException("Could not resolve index part")

    fun getByteIndexAndSize(bytes: ByteArray, sourceStart: Int = 0, sourceEnd: Int = bytes.size) =
        if (fromByteIndex == null) {
            resolveByteIndexAndSize(bytes, sourceStart, sourceEnd)
        } else {
            null
        }

    fun match(
        bytes: ByteArray,
        offset: Int = 0,
        length: Int = bytes.size - offset,
        sourceEnd: Int = bytes.size
    ): Boolean

    private fun resolveByteIndexAndSize(
        bytes: ByteArray,
        sourceStart: Int,
        sourceEnd: Int
    ): Pair<Int, Int> {
        var lastError: ParseException? = null

        for (partCount in indexPartCount downTo indexableIndex + 1) {
            try {
                return findByteIndexAndSizeByPartIndex(
                    indexableIndex,
                    bytes,
                    keySize,
                    sourceStart = sourceStart,
                    sourceEnd = sourceEnd,
                    indexPartCount = partCount
                )
            } catch (error: ParseException) {
                lastError = error
            }
        }

        throw lastError ?: ParseException("Could not resolve index part")
    }
}

/** Matcher for exact matches */
data class IndexPartialToMatch(
    override val indexableIndex: Int,
    override val fromByteIndex: Int?,
    override val keySize: Int,
    override val indexPartCount: Int,
    val toMatch: ByteArray,
    val partialMatch: Boolean = false
) : IsIndexPartialToMatch {
    /** Matches [bytes] to partial and returns true if matches */
    override fun match(bytes: ByteArray, offset: Int, length: Int, sourceEnd: Int) =
        getByteIndexAndSize(bytes, offset, sourceEnd)?.let { (byteIndex, size) ->
            if (partialMatch) {
                bytes.matchesRangePart(offset + byteIndex, toMatch, size)
            } else {
                bytes.matchesRange(offset + byteIndex, toMatch, size)
            }
        } ?: run {
            bytes.matchesRangePart(offset + getByteIndex(bytes, offset, sourceEnd), toMatch, length)
        }
}

/** Matcher for regex matches */
data class IndexPartialToRegexMatch(
    override val indexableIndex: Int,
    override val keySize: Int,
    override val indexPartCount: Int,
    val regex: Regex,
    val valueTransformer: ((String) -> String)? = null
) : IsIndexPartialToMatch {
    // Cannot be set because is string, so needs to be encoded
    override val fromByteIndex: Int? = null

    /** Matches [bytes] to partial and returns true if matches */
    override fun match(bytes: ByteArray, offset: Int, length: Int, sourceEnd: Int): Boolean {
        val (internalOffset, size) = findByteIndexAndSizeByPartIndex(
            indexableIndex,
            bytes,
            keySize,
            sourceStart = offset,
            sourceEnd = sourceEnd,
            indexPartCount = indexPartCount
        )
        val fullOffset = offset + internalOffset
        val stringValue = bytes.decodeToString(fullOffset, fullOffset + size)
        return regex.matches(valueTransformer?.invoke(stringValue) ?: stringValue)
    }
}

/** Size matcher for exact matches in partials */
data class IndexPartialSizeToMatch(
    override val indexableIndex: Int,
    override val fromByteIndex: Int?,
    override val keySize: Int,
    override val indexPartCount: Int,
    val size: Int
) : IsIndexPartialToMatch {
    /** Matches size encoded in [bytes] to partial size and returns true if matches */
    override fun match(bytes: ByteArray, offset: Int, length: Int, sourceEnd: Int) =
        getByteIndexAndSize(bytes, offset, sourceEnd)?.second == size
}

/** Partial [toBeSmaller] for indexable part from [fromByteIndex]. If [inclusive] then include value itself too  */
data class IndexPartialToBeBigger(
    override val indexableIndex: Int,
    override val fromByteIndex: Int?,
    override val keySize: Int,
    override val indexPartCount: Int,
    val toBeSmaller: ByteArray,
    val inclusive: Boolean
) : IsIndexPartialToMatch {
    /** Matches [bytes] to be bigger to partial and returns true if is bigger */
    override fun match(bytes: ByteArray, offset: Int, length: Int, sourceEnd: Int): Boolean {
        getByteIndexAndSize(bytes, offset, sourceEnd)?.let { (byteIndex, size) ->
            return toBeSmaller.compareToRange(bytes, offset + byteIndex, size).let { comparison ->
                comparison < 0 || (inclusive && comparison == 0)
            }
        }

        val fromIndex = offset + getByteIndex(bytes, offset, sourceEnd)
        toBeSmaller.forEachIndexed { index, byte ->
            val smallerByte = byte.toUByte()
            val biggerByte = bytes[index + fromIndex].toUByte()
            when {
                smallerByte > biggerByte -> return false
                smallerByte < biggerByte -> return true
                // else continue scanning
            }
        }
        return inclusive
    }
}

/** Partial [toBeBigger] for indexable part from [fromByteIndex]. If [inclusive] then include value itself too */
data class IndexPartialToBeSmaller(
    override val indexableIndex: Int,
    override val fromByteIndex: Int?,
    override val keySize: Int,
    override val indexPartCount: Int,
    val toBeBigger: ByteArray,
    val inclusive: Boolean
) : IsIndexPartialToMatch {
    /** Matches [bytes] to be smaller to partial and returns true if is smaller */
    override fun match(bytes: ByteArray, offset: Int, length: Int, sourceEnd: Int): Boolean {
        getByteIndexAndSize(bytes, offset, sourceEnd)?.let { (byteIndex, size) ->
            return toBeBigger.compareToRange(bytes, offset + byteIndex, size).let { comparison ->
                comparison > 0 || (inclusive && comparison == 0)
            }
        }

        val fromIndex = offset + getByteIndex(bytes, offset, sourceEnd)
        toBeBigger.forEachIndexed { index, byte ->
            val biggerByte = byte.toUByte()
            val smallerByte = bytes[index + fromIndex].toUByte()
            when {
                smallerByte > biggerByte -> return false
                smallerByte < biggerByte -> return true
                // else continue scanning
            }
        }
        return inclusive
    }
}

/** Partial for indexable to be one of given bytearrays. [toBeOneOf] needs to be sorted */
data class IndexPartialToBeOneOf(
    override val indexableIndex: Int,
    override val fromByteIndex: Int?,
    override val keySize: Int,
    override val indexPartCount: Int,
    val toBeOneOf: List<ByteArray>,
    val partialMatch: Boolean = false,
) : IsIndexPartialToMatch {
    /** Matches [bytes] to be one of partials in list */
    override fun match(bytes: ByteArray, offset: Int, length: Int, sourceEnd: Int) =
        getByteIndexAndSize(bytes, offset, sourceEnd)?.let { (byteIndex, size) ->
            if (partialMatch) {
                toBeOneOf.any { bytes.matchesRangePart(offset + byteIndex, it, size, length = it.size) }
            } else {
                toBeOneOf.any { bytes.matchesRange(offset + byteIndex, it, size) }
            }
        } ?: toBeOneOf.any { bytes.matchesRangePart(offset + getByteIndex(bytes, offset, sourceEnd), it, length) }
}
