package maryk.core.properties.exceptions

/** Exception with [cause] for when parsing to [value] fails. */
class ParseException internal constructor(
    value: String,
    cause: Throwable? = null
) : Throwable(
    "Property value could not be parsed: $value",
    cause
)