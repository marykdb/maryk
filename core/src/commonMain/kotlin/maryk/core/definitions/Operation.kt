package maryk.core.definitions

import maryk.core.properties.enum.IndexedEnumComparable
import maryk.core.properties.enum.IndexedEnumDefinition
import maryk.core.properties.enum.IsCoreEnum
import maryk.core.properties.enum.TypeEnum
import maryk.core.query.requests.IsOperation

enum class Operation(
    override val index: UInt
) : IndexedEnumComparable<Operation>, TypeEnum<IsOperation>, IsCoreEnum {
    Define(1u), Request(2u);

    companion object : IndexedEnumDefinition<Operation>(
        Operation::class, ::values
    )
}
