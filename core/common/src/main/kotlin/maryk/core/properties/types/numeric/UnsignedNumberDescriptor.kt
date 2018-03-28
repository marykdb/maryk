package maryk.core.properties.types.numeric

import maryk.core.protobuf.WireType

abstract class UnsignedNumberDescriptor<T: Comparable<T>>(
    size: Int,
    type: NumberType,
    internal val MIN_VALUE: T,
    internal val MAX_VALUE: T
): NumberDescriptor<T>(size, WireType.VAR_INT, type)