package maryk.core.query.responses.statuses

import maryk.core.properties.enum.IndexedEnumComparable
import maryk.core.properties.enum.IndexedEnumDefinition
import maryk.core.properties.enum.IsCoreEnum
import maryk.core.properties.enum.TypeEnum

/** Indexed type of changes */
enum class StatusType(
    override val index: UInt,
    override val alternativeNames: Set<String>? = null
) : IndexedEnumComparable<StatusType>, TypeEnum<IsResponseStatus>, IsCoreEnum {
    ADD_SUCCESS(1u),
    CHANGE_SUCCESS(2u),
    DELETE_SUCCESS(3u),
    AUTH_FAIL(4u),
    REQUEST_FAIL(5u),
    SERVER_FAIL(6u),
    VALIDATION_FAIL(7u),
    ALREADY_EXISTS(8u),
    DOES_NOT_EXIST(9u);

    companion object : IndexedEnumDefinition<StatusType>(
        "StatusType", StatusType::values
    )
}
