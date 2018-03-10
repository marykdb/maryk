package maryk.core.properties.types.numeric

/** Base class for unsigned integers */
abstract class UInt<T: Number> internal constructor(internal val number: T): Comparable<UInt<T>> {
    override fun hashCode() = number.hashCode()
    override fun equals(other: Any?) =
        when (other) {
            is Float -> {
                if (other % 1 != 0F) {
                    false
                } else {
                    this.toInt() == other.toInt()
                }
            }
            is Double -> {
                if (other % 1 != 0.0) {
                    false
                } else if(other > UInt32.MAX_VALUE.toLong()) {
                    false
                } else {
                    this.toInt() == other.toInt()
                }
            }
            is Int, is Short, is Byte -> {
                this.toInt() == other
            }
            is Long -> {
                this.toLong() == other
            }
            is UInt8 -> this.toInt() == other.toInt()
            is UInt16 -> this.toInt() == other.toInt()
            is UInt32 -> this.toInt() == other.toInt()
            is UInt64 -> this.toLong() == other.toLong()
            else -> false
        }

    abstract fun toInt(): Int
    abstract fun toLong(): Long
}
