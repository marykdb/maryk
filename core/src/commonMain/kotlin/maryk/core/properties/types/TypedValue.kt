package maryk.core.properties.types

import maryk.core.properties.enum.TypeEnum

/** Describes a typed value which can be used as multi type property */
interface TypedValue<out E : TypeEnum<T>, out T : Any> {
    val type: E
    val value: T
}

/** Constructs an immutable typed value */
@Suppress("FunctionName")
fun <E : TypeEnum<T>, T : Any> TypedValue(
    type: E,
    value: T
) = TypedValueImpl(type, value)

/** An immutable typed value */
data class TypedValueImpl<out E : TypeEnum<T>, out T : Any> internal constructor(
    override val type: E,
    override val value: T
): TypedValue<E, T>

/** A mutable typed value */
data class MutableTypedValue<out E : TypeEnum<T>, T : Any>(
    override val type: E,
    override var value: T
): TypedValue<E, T>
