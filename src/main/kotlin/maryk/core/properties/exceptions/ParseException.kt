package maryk.core.properties.exceptions

/**
 * Exception for when parsing to native value fails.
 */
class ParseException(
        value: String,
        cause: Throwable? = null
) : Throwable(
        "Property value could not be parsed: $value",
        cause
)