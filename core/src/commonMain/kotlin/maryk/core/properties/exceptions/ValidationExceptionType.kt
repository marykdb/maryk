package maryk.core.properties.exceptions

import maryk.core.properties.enum.IndexedEnumComparable
import maryk.core.properties.enum.IndexedEnumDefinition
import maryk.core.properties.enum.IsCoreEnum
import maryk.core.properties.enum.TypeEnum

/** Indexed type of changes */
enum class ValidationExceptionType(
    override val index: UInt,
    override val alternativeNames: Set<String>? = null
) : IndexedEnumComparable<ValidationExceptionType>, IsCoreEnum, TypeEnum<ValidationException> {
    ALREADY_EXISTS(1u),
    ALREADY_SET(2u),
    INVALID_VALUE(3u),
    INVALID_SIZE(4u),
    OUT_OF_RANGE(5u),
    REQUIRED(6u),
    NOT_ENOUGH_ITEMS(7u),
    TOO_MANY_ITEMS(8u),
    UMBRELLA(9u);

    companion object : IndexedEnumDefinition<ValidationExceptionType>(
        "ValidationExceptionType", ValidationExceptionType::values
    )
}
