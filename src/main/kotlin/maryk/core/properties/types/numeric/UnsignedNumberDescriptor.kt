package maryk.core.properties.types.numeric

abstract class UnsignedNumberDescriptor<T: Comparable<T>>(
        size: Int,
        val MIN_VALUE: T,
        val MAX_VALUE: T
): NumberDescriptor<T>(size)