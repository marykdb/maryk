package maryk.core.properties

import maryk.core.models.DataModel
import maryk.core.models.IsValuesDataModel
import maryk.core.properties.definitions.IsPropertyDefinition
import maryk.core.properties.references.AnyOutPropertyReference
import maryk.core.properties.references.IsPropertyReference

interface IsModel: IsValuesPropertyDefinitions {
    override val Model: IsValuesDataModel<*>
}

open class Model<P: IsValuesPropertyDefinitions>(
    reservedIndices: List<UInt>? = null,
    reservedNames: List<String>? = null,
) : TypedValuesModel<DataModel<P>, P>(), IsModel {
    @Suppress("UNCHECKED_CAST")
    override val Model = DataModel(
        reservedIndices = reservedIndices,
        reservedNames = reservedNames,
        properties = this,
    ) as DataModel<P>

    @Suppress("UNCHECKED_CAST")
    operator fun <T : Any, R : IsPropertyReference<T, IsPropertyDefinition<T>, *>> invoke(
        parent: AnyOutPropertyReference? = null,
        referenceGetter: P.() -> (AnyOutPropertyReference?) -> R
    ) = referenceGetter(this as P)(parent)

    operator fun <R> invoke(block: P.() -> R): R {
        @Suppress("UNCHECKED_CAST")
        return block(this as P)
    }
}
