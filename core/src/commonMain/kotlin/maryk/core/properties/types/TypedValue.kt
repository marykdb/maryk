package maryk.core.properties.types

import maryk.core.properties.enum.TypeEnum

data class TypedValue<out E : TypeEnum<T>, out T : Any>(
    val type: E,
    val value: T
)
