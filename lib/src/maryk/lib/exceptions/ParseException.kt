package maryk.lib.exceptions

/** Exception with [cause] for when parsing to value fails. */
class ParseException(
    value: String,
    cause: Throwable? = null
) : Exception(
    "Property value could not be parsed: $value",
    cause
)
