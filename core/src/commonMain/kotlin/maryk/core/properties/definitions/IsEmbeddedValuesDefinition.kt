package maryk.core.properties.definitions

import maryk.core.models.IsValuesDataModel
import maryk.core.properties.IsPropertyContext
import maryk.core.properties.PropertyDefinitions
import maryk.core.values.Values

/** Interface for property definitions containing embedded Values of model [DM] and context [CX]. */
interface IsEmbeddedValuesDefinition<DM : IsValuesDataModel<P>, P : PropertyDefinitions, CX : IsPropertyContext> :
    IsValueDefinition<Values<DM, P>, CX>,
    HasDefaultValueDefinition<Values<DM, P>>,
    IsEmbeddedDefinition<DM, P>,
    IsUsableInMultiType<Values<DM, P>, CX>,
    IsUsableInMapValue<Values<DM, P>, CX> {
    override val dataModel: DM

    override fun compatibleWith(
        definition: IsPropertyDefinition<*>,
        addIncompatibilityReason: ((String) -> Unit)?
    ): Boolean {
        var compatible = super<IsValueDefinition>.compatibleWith(definition, addIncompatibilityReason)

        (definition as? IsEmbeddedValuesDefinition<*, *, *>)?.let {
            compatible = this.compatibleWithDefinitionWithDataModel(definition, addIncompatibilityReason) && compatible
        }

        return compatible
    }
}
