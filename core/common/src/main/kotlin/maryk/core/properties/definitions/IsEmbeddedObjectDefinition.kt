package maryk.core.properties.definitions

import maryk.core.models.AbstractObjectDataModel
import maryk.core.properties.IsPropertyContext
import maryk.core.properties.ObjectPropertyDefinitions

/** Interface for property definitions containing embedded DataObjects of [DO] and context [CX]. */
interface IsEmbeddedObjectDefinition<DO : Any, P: ObjectPropertyDefinitions<DO>, out DM : AbstractObjectDataModel<DO, P, CXI, CX>, CXI: IsPropertyContext, CX: IsPropertyContext> :
    IsValueDefinition<DO, CXI>,
    IsTransportablePropertyDefinitionType<DO>,
    HasDefaultValueDefinition<DO>
{
    val dataModel: DM
}
