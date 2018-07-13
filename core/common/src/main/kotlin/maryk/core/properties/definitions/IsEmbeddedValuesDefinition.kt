package maryk.core.properties.definitions

import maryk.core.models.IsValuesDataModel
import maryk.core.objects.Values
import maryk.core.properties.IsPropertyContext
import maryk.core.properties.PropertyDefinitions

/** Interface for property definitions containing embedded Values of model [DM] and context [CX]. */
interface IsEmbeddedValuesDefinition<DM : IsValuesDataModel<P>, P: PropertyDefinitions, CX: IsPropertyContext> :
    IsValueDefinition<Values<DM, P>, CX>,
    IsTransportablePropertyDefinitionType<Values<DM, P>>,
    HasDefaultValueDefinition<Values<DM, P>>
{
    val dataModel: DM
}
