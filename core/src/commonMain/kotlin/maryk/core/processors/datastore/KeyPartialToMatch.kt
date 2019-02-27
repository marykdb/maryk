package maryk.core.processors.datastore

import maryk.lib.extensions.compare.matchPart

sealed class IsKeyPartialToMatch {
    abstract val fromIndex: Int
    abstract fun match(bytes: ByteArray): Boolean
}

internal class KeyPartialToMatch(
    override val fromIndex: Int,
    val toMatch: ByteArray
) : IsKeyPartialToMatch() {
    /** Matches [bytes] to partial and returns true if matches */
    override fun match(bytes: ByteArray) = bytes.matchPart(fromIndex, toMatch)
}

/** Partial [toBeSmaller] for key part from [fromIndex]. If [inclusive] then include value itself too  */
internal class KeyPartialToBeBigger(
    override val fromIndex: Int,
    val toBeSmaller: ByteArray,
    val inclusive: Boolean
) : IsKeyPartialToMatch() {
    /** Matches [bytes] to be bigger to partial and returns true if is bigger */
    override fun match(bytes: ByteArray): Boolean {
        toBeSmaller.forEachIndexed { index, byte ->
            val smallerByte = byte.toUByte()
            val biggerByte = bytes[index + this.fromIndex].toUByte()
            when {
                smallerByte > biggerByte -> return false
                smallerByte < biggerByte -> return true
                // else continue scanning
            }
        }
        return inclusive
    }
}

/** Partial [toBeBigger] for key part from [fromIndex]. If [inclusive] then include value itself too */
internal class KeyPartialToBeSmaller(override val fromIndex: Int, val toBeBigger: ByteArray, val inclusive: Boolean) :
    IsKeyPartialToMatch() {
    /** Matches [bytes] to be smaller to partial and returns true if is smaller */
    override fun match(bytes: ByteArray): Boolean {
        toBeBigger.forEachIndexed { index, byte ->
            val biggerByte = byte.toUByte()
            val smallerByte = bytes[index + this.fromIndex].toUByte()
            when {
                smallerByte > biggerByte -> return false
                smallerByte < biggerByte -> return true
                // else continue scanning
            }
        }
        return inclusive
    }
}

/** Partial for key to be one of given bytearrays. [toBeOneOf] needs to be sorted */
internal class KeyPartialToBeOneOf(override val fromIndex: Int, val toBeOneOf: List<ByteArray>) :
    IsKeyPartialToMatch() {
    /** Matches [bytes] to be one of partials in list */
    override fun match(bytes: ByteArray): Boolean {
        for (item in toBeOneOf) {
            if (bytes.matchPart(this.fromIndex, item)) return true
        }
        return false
    }
}
