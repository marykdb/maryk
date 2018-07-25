package maryk.core.properties.exceptions

import maryk.core.properties.enum.IndexedEnum
import maryk.core.properties.enum.IndexedEnumDefinition

/** Indexed type of changes */
enum class ValidationExceptionType(
    override val index: Int
): IndexedEnum<ValidationExceptionType> {
    ALREADY_SET(1),
    INVALID_VALUE(2),
    INVALID_SIZE(3),
    OUT_OF_RANGE(4),
    REQUIRED(5),
    NOT_ENOUGH_ITEMS(6),
    TOO_MUCH_ITEMS(7),
    UMBRELLA(8);

    companion object: IndexedEnumDefinition<ValidationExceptionType>(
        "ValidationExceptionType", ValidationExceptionType::values
    )
}
