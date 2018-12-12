package maryk.core.processors.datastore


interface IsKeyPartialToMatch {
    val fromIndex: Int
    fun match(bytes: ByteArray): Boolean
}

internal class KeyPartialToMatch(override val fromIndex: Int, val toMatch: ByteArray): IsKeyPartialToMatch {
    /** Matches [bytes] to partial and returns true if matches */
    override fun match(bytes: ByteArray): Boolean {
        toMatch.forEachIndexed { index, byte ->
            if(bytes[index + this.fromIndex] != byte) return false
        }
        return true
    }
}

/** Partial [toBeBigger] for key part from [fromIndex]. If [inclusive] then include value itself too  */
internal class KeyPartialToBeBigger(override val fromIndex: Int, val toBeBigger: ByteArray, val inclusive: Boolean): IsKeyPartialToMatch {
    /** Matches [bytes] to be bigger to partial and returns true if is bigger */
    override fun match(bytes: ByteArray) = TODO("NI") //if (inclusive) { toBeBigger >= bytes } else { toBeBigger > bytes }
}

/** Partial [toBeSmaller] for key part from [fromIndex]. If [inclusive] then include value itself too */
internal class KeyPartialToBeSmaller(override val fromIndex: Int, val toBeSmaller: ByteArray, val inclusive: Boolean): IsKeyPartialToMatch {
    /** Matches [bytes] to be smaller to partial and returns true if is smaller */
    override fun match(bytes: ByteArray) = TODO("NI")//if (inclusive) { toBeSmaller <= bytes } else { toBeSmaller > bytes }
}

/** Partial for key to be one of given bytearrays. [toBeOneOf] needs to be sorted */
internal class KeyPartialToBeOneOf(override val fromIndex: Int, val toBeOneOf: List<ByteArray>): IsKeyPartialToMatch {
    /** Matches [bytes] to be one of partials in list */
    override fun match(bytes: ByteArray) = TODO("NI")//if (inclusive) { toBeBigger >= bytes } else { toBeBigger > bytes }
}
