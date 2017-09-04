package maryk.core.properties.types

data class TypedValue<out T: Any>(val typeIndex: Int, val value: T)
