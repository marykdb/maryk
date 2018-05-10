package maryk.core.query.responses.statuses

import maryk.core.properties.types.IndexedEnum
import maryk.core.properties.types.IndexedEnumDefinition

/** Indexed type of changes */
enum class StatusType(
        override val index: Int
): IndexedEnum<StatusType> {
    SUCCESS(0),
    ADD_SUCCESS(1),
    AUTH_FAIL(2),
    REQUEST_FAIL(3),
    SERVER_FAIL(4),
    VALIDATION_FAIL(5),
    ALREADY_EXISTS(6),
    DOES_NOT_EXIST(7);

    companion object: IndexedEnumDefinition<StatusType>(
        "StatusType", StatusType::values
    )
}
