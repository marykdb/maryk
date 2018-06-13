package maryk.core.definitions

import maryk.core.properties.enum.IndexedEnum
import maryk.core.properties.enum.IndexedEnumDefinition

enum class Operation(
    override val index: Int
) : IndexedEnum<Operation> {
    Define(0), Request(1);

    companion object : IndexedEnumDefinition<Operation>(
        "Operation", { Operation.values() }
    )
}
