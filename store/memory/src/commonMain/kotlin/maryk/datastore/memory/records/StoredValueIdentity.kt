package maryk.datastore.memory.records

import maryk.core.properties.types.numeric.NumberType
import maryk.core.properties.types.numeric.NumberType.Float32Type
import maryk.core.properties.types.numeric.NumberType.Float64Type

/** Unique floating claims retain the raw NaN identities used by persistent ordinary value codecs. */
internal fun <T : Comparable<T>> compareUniqueValues(left: T, right: T, numberType: NumberType?): Int = when {
    numberType == Float32Type && (left as Float).isNaN() && (right as Float).isNaN() ->
        left.toRawBits().compareTo(right.toRawBits())
    numberType == Float64Type && (left as Double).isNaN() && (right as Double).isNaN() ->
        left.toRawBits().compareTo(right.toRawBits())
    left is Double && right is Double && left.isNaN() && right.isNaN() ->
        left.toRawBits().compareTo(right.toRawBits())
    left is Float && right is Float && left.isNaN() && right.isNaN() ->
        left.toRawBits().compareTo(right.toRawBits())
    else -> left.compareTo(right)
}

/** A raw NaN payload change must update both its stored value and its unique claim. */
internal fun haveSameStoredValue(left: Any?, right: Any?): Boolean = when {
    left is Double && right is Double && left.isNaN() && right.isNaN() -> left.toRawBits() == right.toRawBits()
    left is Float && right is Float && left.isNaN() && right.isNaN() -> left.toRawBits() == right.toRawBits()
    else -> left == right
}
