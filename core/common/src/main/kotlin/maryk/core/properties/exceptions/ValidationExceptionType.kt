package maryk.core.properties.exceptions

import maryk.core.properties.types.IndexedEnum
import maryk.core.properties.types.IndexedEnumDefinition

/** Indexed type of changes */
enum class ValidationExceptionType(
    override val index: Int
): IndexedEnum<ValidationExceptionType> {
    ALREADY_SET(0),
    INVALID_VALUE(1),
    INVALID_SIZE(2),
    OUT_OF_RANGE(3),
    REQUIRED(4),
    NOT_ENOUGH_ITEMS(5),
    TOO_MUCH_ITEMS(6),
    UMBRELLA(7);

    companion object: IndexedEnumDefinition<ValidationExceptionType>(
        "ValidationExceptionType", ValidationExceptionType::values
    )
}
