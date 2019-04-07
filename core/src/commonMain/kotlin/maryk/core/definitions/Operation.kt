package maryk.core.definitions

import maryk.core.properties.enum.IndexedEnum
import maryk.core.properties.enum.IndexedEnumDefinition
import maryk.core.properties.enum.IsCoreEnum

enum class Operation(
    override val index: UInt
) : IndexedEnum<Operation>, IsCoreEnum {
    Define(1u), Request(2u);

    companion object : IndexedEnumDefinition<Operation>(
        "Operation", { Operation.values() }
    )
}
