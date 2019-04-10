package maryk.core.properties.types

import maryk.core.properties.enum.IndexedEnum

data class TypedValue<E : IndexedEnum, out T : Any>(
    val type: E,
    val value: T
)
