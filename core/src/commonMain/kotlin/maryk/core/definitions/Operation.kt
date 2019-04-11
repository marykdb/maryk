package maryk.core.definitions

import maryk.core.properties.enum.IndexedEnumComparable
import maryk.core.properties.enum.IndexedEnumDefinition
import maryk.core.properties.enum.IsCoreEnum

enum class Operation(
    override val index: UInt
) : IndexedEnumComparable<Operation>, IsCoreEnum {
    Define(1u), Request(2u);

    companion object : IndexedEnumDefinition<Operation>(
        Operation::class, ::values
    )
}
