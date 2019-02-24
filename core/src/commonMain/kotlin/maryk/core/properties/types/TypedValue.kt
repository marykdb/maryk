package maryk.core.properties.types

import maryk.core.properties.enum.IndexedEnum

data class TypedValue<E : IndexedEnum<E>, out T : Any>(
    val type: E,
    val value: T
)
