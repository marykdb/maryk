package maryk.core.processors.datastore

import maryk.lib.extensions.compare.matchPart

sealed class IsIndexPartialToMatch {
    abstract val indexableIndex: Int
    abstract val fromByteIndex: Int?

    /**
     * Returns byte index to start reading for [bytes]
     * If this partial is for key bytes it already has the value or otherwise is for flex key then will calculate value
     */
    protected fun getByteIndex(bytes: ByteArray) = fromByteIndex ?: findByteIndexByPartIndex(indexableIndex, bytes)

    abstract fun match(bytes: ByteArray): Boolean
}

internal class IndexPartialToMatch(
    override val indexableIndex: Int,
    override val fromByteIndex: Int?,
    val toMatch: ByteArray
) : IsIndexPartialToMatch() {
    /** Matches [bytes] to partial and returns true if matches */
    override fun match(bytes: ByteArray) = bytes.matchPart(getByteIndex(bytes), toMatch)
}

/** Partial [toBeSmaller] for indexable part from [fromByteIndex]. If [inclusive] then include value itself too  */
internal class IndexPartialToBeBigger(
    override val indexableIndex: Int,
    override val fromByteIndex: Int?,
    val toBeSmaller: ByteArray,
    val inclusive: Boolean
) : IsIndexPartialToMatch() {
    /** Matches [bytes] to be bigger to partial and returns true if is bigger */
    override fun match(bytes: ByteArray): Boolean {
        val fromIndex = getByteIndex(bytes)
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
internal class IndexPartialToBeSmaller(
    override val indexableIndex: Int,
    override val fromByteIndex: Int?,
    val toBeBigger: ByteArray,
    val inclusive: Boolean
) : IsIndexPartialToMatch() {
    /** Matches [bytes] to be smaller to partial and returns true if is smaller */
    override fun match(bytes: ByteArray): Boolean {
        val fromIndex = getByteIndex(bytes)
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
internal class IndexPartialToBeOneOf(
    override val indexableIndex: Int,
    override val fromByteIndex: Int?,
    val toBeOneOf: List<ByteArray>
) : IsIndexPartialToMatch() {
    /** Matches [bytes] to be one of partials in list */
    override fun match(bytes: ByteArray): Boolean {
        val fromIndex = getByteIndex(bytes)
        for (item in toBeOneOf) {
            if (bytes.matchPart(fromIndex, item)) return true
        }
        return false
    }
}
