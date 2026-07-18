package maryk.core.properties.definitions

import maryk.core.properties.IsPropertyContext

/** Definition for comparable values which support arithmetic aggregations. */
interface IsArithmeticDefinition<T : Comparable<T>> :
    IsComparableDefinition<T, IsPropertyContext> {
    /** Add two values using this definition's overflow and precision semantics. */
    fun add(value1: T, value2: T): T

    /** Calculate an average at this definition's result precision. */
    fun average(sum: T, count: Long): T
}
