package maryk.core.properties.definitions

import maryk.core.properties.IsPropertyContext
import maryk.core.properties.IsValuesPropertyDefinitions
import maryk.core.values.Values

/** Interface for property definitions containing embedded Values of model [DM] and context [CX]. */
interface IsEmbeddedValuesDefinition<DM : IsValuesPropertyDefinitions, CX : IsPropertyContext> :
    IsValueDefinition<Values<DM>, CX>,
    HasDefaultValueDefinition<Values<DM>>,
    IsEmbeddedDefinition<DM>,
    IsUsableInMultiType<Values<DM>, CX>,
    IsUsableInMapValue<Values<DM>, CX> {
    override val dataModel: DM

    override fun compatibleWith(
        definition: IsPropertyDefinition<*>,
        addIncompatibilityReason: ((String) -> Unit)?
    ): Boolean {
        var compatible = super<IsValueDefinition>.compatibleWith(definition, addIncompatibilityReason)

        (definition as? IsEmbeddedValuesDefinition<*, *>)?.let {
            compatible = this.compatibleWithDefinitionWithDataModel(definition, addIncompatibilityReason) && compatible
        }

        return compatible
    }
}
