package maryk.core.definitions

import maryk.core.properties.types.IndexedEnum
import maryk.core.properties.types.IndexedEnumDefinition

enum class Operation(
    override val index: Int
) : IndexedEnum<Operation> {
    Define(0), Request(1);

    companion object : IndexedEnumDefinition<Operation>(
        "Operation", { Operation.values() }
    )
}
