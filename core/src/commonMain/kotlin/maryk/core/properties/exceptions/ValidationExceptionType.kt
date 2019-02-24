package maryk.core.properties.exceptions

import maryk.core.properties.enum.IndexedEnum
import maryk.core.properties.enum.IndexedEnumDefinition

/** Indexed type of changes */
enum class ValidationExceptionType(
    override val index: UInt
) : IndexedEnum<ValidationExceptionType> {
    ALREADY_SET(1u),
    INVALID_VALUE(2u),
    INVALID_SIZE(3u),
    OUT_OF_RANGE(4u),
    REQUIRED(5u),
    NOT_ENOUGH_ITEMS(6u),
    TOO_MANY_ITEMS(7u),
    UMBRELLA(8u);

    companion object : IndexedEnumDefinition<ValidationExceptionType>(
        "ValidationExceptionType", ValidationExceptionType::values
    )
}
