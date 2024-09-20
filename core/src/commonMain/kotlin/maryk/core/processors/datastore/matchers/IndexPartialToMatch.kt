package maryk.core.processors.datastore.matchers

import maryk.core.processors.datastore.findByteIndexAndSizeByPartIndex
import maryk.core.processors.datastore.findByteIndexByPartIndex
import maryk.lib.extensions.compare.matchPart

sealed class IsIndexPartialToMatch {
    abstract val indexableIndex: Int
    abstract val fromByteIndex: Int?
    abstract val keySize: Int

    /**
     * Returns byte index to start reading for [bytes]
     * If this partial is for key bytes it already has the value or otherwise is for flex key then will calculate value
     */
    protected fun getByteIndex(bytes: ByteArray) = fromByteIndex ?: findByteIndexByPartIndex(
        indexableIndex,
        bytes,
        keySize
    )

    abstract fun match(bytes: ByteArray, offset: Int = 0, length: Int = bytes.size - offset): Boolean
}

/** Matcher for exact matches */
class IndexPartialToMatch(
    override val indexableIndex: Int,
    override val fromByteIndex: Int?,
    override val keySize: Int,
    val toMatch: ByteArray,
    val partialMatch: Boolean = false
) : IsIndexPartialToMatch() {
    /** Matches [bytes] to partial and returns true if matches */
    override fun match(bytes: ByteArray, offset: Int, length: Int) =
        bytes.matchPart(offset + getByteIndex(bytes), toMatch, length)
}

/** Matcher for regex matches */
class IndexPartialToRegexMatch(
    override val indexableIndex: Int,
    override val keySize: Int,
    val regex: Regex
) : IsIndexPartialToMatch() {
    // Cannot be set because is string, so needs to be encoded
    override val fromByteIndex: Int? = null

    /** Matches [bytes] to partial and returns true if matches */
    override fun match(bytes: ByteArray, offset: Int, length: Int): Boolean {
        val (internalOffset, size) = findByteIndexAndSizeByPartIndex(indexableIndex, bytes, keySize)
        val fullOffset = offset + internalOffset
        return regex.matches(bytes.decodeToString(fullOffset, fullOffset + size))
    }
}

/** Size matcher for exact matches in partials */
class IndexPartialSizeToMatch(
    override val indexableIndex: Int,
    override val fromByteIndex: Int?,
    override val keySize: Int,
    val size: Int
) : IsIndexPartialToMatch() {
    /** Matches size encoded in [bytes] to partial size and returns true if matches */
    override fun match(bytes: ByteArray, offset: Int, length: Int) =
        findByteIndexAndSizeByPartIndex(indexableIndex, bytes, keySize).second == size
}

/** Partial [toBeSmaller] for indexable part from [fromByteIndex]. If [inclusive] then include value itself too  */
class IndexPartialToBeBigger(
    override val indexableIndex: Int,
    override val fromByteIndex: Int?,
    override val keySize: Int,
    val toBeSmaller: ByteArray,
    val inclusive: Boolean
) : IsIndexPartialToMatch() {
    /** Matches [bytes] to be bigger to partial and returns true if is bigger */
    override fun match(bytes: ByteArray, offset: Int, length: Int): Boolean {
        val fromIndex = offset + getByteIndex(bytes)
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
class IndexPartialToBeSmaller(
    override val indexableIndex: Int,
    override val fromByteIndex: Int?,
    override val keySize: Int,
    val toBeBigger: ByteArray,
    val inclusive: Boolean
) : IsIndexPartialToMatch() {
    /** Matches [bytes] to be smaller to partial and returns true if is smaller */
    override fun match(bytes: ByteArray, offset: Int, length: Int): Boolean {
        val fromIndex = offset + getByteIndex(bytes)
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
class IndexPartialToBeOneOf(
    override val indexableIndex: Int,
    override val fromByteIndex: Int?,
    override val keySize: Int,
    val toBeOneOf: List<ByteArray>
) : IsIndexPartialToMatch() {
    /** Matches [bytes] to be one of partials in list */
    override fun match(bytes: ByteArray, offset: Int, length: Int) =
        toBeOneOf.any { bytes.matchPart(offset + getByteIndex(bytes), it, length) }
}
