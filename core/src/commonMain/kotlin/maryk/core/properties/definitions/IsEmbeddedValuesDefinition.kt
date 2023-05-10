package maryk.core.properties.definitions

import maryk.core.models.IsValuesDataModel
import maryk.core.properties.IsPropertyContext
import maryk.core.values.Values

/** Interface for property definitions containing embedded Values of model [DM] and context [CX]. */
interface IsEmbeddedValuesDefinition<DM : IsValuesDataModel, CX : IsPropertyContext> :
    IsValueDefinition<Values<DM>, CX>,
    HasDefaultValueDefinition<Values<DM>>,
    IsEmbeddedDefinition<DM>,
    IsUsableInMultiType<Values<DM>, CX>,
    IsUsableInMapValue<Values<DM>, CX> {
    override val dataModel: DM

    override fun compatibleWith(
        definition: IsPropertyDefinition<*>,
        handledPrimitiveNames: MutableSet<String>,
        addIncompatibilityReason: ((String) -> Unit)?
    ): Boolean {
        var compatible = super<IsValueDefinition>.compatibleWith(definition, handledPrimitiveNames, addIncompatibilityReason)

        (definition as? IsEmbeddedValuesDefinition<*, *>)?.let {
            compatible = this.compatibleWithDefinitionWithDataModel(definition, handledPrimitiveNames, addIncompatibilityReason) && compatible
        }

        return compatible
    }
}
