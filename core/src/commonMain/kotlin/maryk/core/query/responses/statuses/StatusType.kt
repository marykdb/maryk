package maryk.core.query.responses.statuses

import maryk.core.properties.enum.IndexedEnumComparable
import maryk.core.properties.enum.IndexedEnumDefinition
import maryk.core.properties.enum.IsCoreEnum
import maryk.core.properties.enum.TypeEnum

/** Indexed type of changes */
enum class StatusType(
    override val index: UInt
) : IndexedEnumComparable<StatusType>, TypeEnum<IsResponseStatus>, IsCoreEnum {
    SUCCESS(1u),
    ADD_SUCCESS(2u),
    AUTH_FAIL(3u),
    REQUEST_FAIL(4u),
    SERVER_FAIL(5u),
    VALIDATION_FAIL(6u),
    ALREADY_EXISTS(7u),
    DOES_NOT_EXIST(8u);

    companion object : IndexedEnumDefinition<StatusType>(
        "StatusType", StatusType::values
    )
}
