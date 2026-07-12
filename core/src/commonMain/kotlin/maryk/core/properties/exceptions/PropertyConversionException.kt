package maryk.core.properties.exceptions

enum class PropertyConversionDirection {
    TO_SERIALIZABLE,
    FROM_SERIALIZABLE,
}

class PropertyConversionException(
    val propertyName: String,
    val direction: PropertyConversionDirection,
    val inputType: String,
    cause: ClassCastException,
) : Exception(
    "Failed to convert property `$propertyName` ${direction.name.lowercase()} from $inputType",
    cause,
)
