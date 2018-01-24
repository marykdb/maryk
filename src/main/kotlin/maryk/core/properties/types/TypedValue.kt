package maryk.core.properties.types

data class TypedValue<E: IndexedEnum<E>, out T: Any>(
    val type: E,
    val value: T
)