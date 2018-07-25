package maryk.core.query.responses.statuses

import maryk.core.properties.enum.IndexedEnum
import maryk.core.properties.enum.IndexedEnumDefinition

/** Indexed type of changes */
enum class StatusType(
        override val index: Int
): IndexedEnum<StatusType> {
    SUCCESS(1),
    ADD_SUCCESS(2),
    AUTH_FAIL(3),
    REQUEST_FAIL(4),
    SERVER_FAIL(5),
    VALIDATION_FAIL(6),
    ALREADY_EXISTS(7),
    DOES_NOT_EXIST(8);

    companion object: IndexedEnumDefinition<StatusType>(
        "StatusType", StatusType::values
    )
}
