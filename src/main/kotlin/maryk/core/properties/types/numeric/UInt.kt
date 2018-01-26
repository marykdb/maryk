package maryk.core.properties.types.numeric

/** Base class for unsigned integers */
abstract class UInt<T: Number> internal constructor(internal val number: T): Comparable<UInt<T>> {
    override fun equals(other: Any?) = when (other) {
        !is UInt<*> -> false
        else -> number == other.number
    }
    override fun hashCode() = number.hashCode()
}
