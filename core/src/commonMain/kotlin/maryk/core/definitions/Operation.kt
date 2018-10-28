package maryk.core.definitions

import maryk.core.properties.enum.IndexedEnum
import maryk.core.properties.enum.IndexedEnumDefinition

enum class Operation(
    override val index: Int
) : IndexedEnum<Operation> {
    Define(1), Request(2);

    companion object : IndexedEnumDefinition<Operation>(
        "Operation", { Operation.values() }
    )
}
