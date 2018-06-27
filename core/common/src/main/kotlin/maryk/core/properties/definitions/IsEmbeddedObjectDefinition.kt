package maryk.core.properties.definitions

import maryk.core.models.AbstractDataModel
import maryk.core.properties.IsPropertyContext

/** Interface for property definitions containing embedded DataObjects of [DO] and context [CX]. */
interface IsEmbeddedObjectDefinition<DO : Any, out P: PropertyDefinitions<DO>, out DM : AbstractDataModel<DO, P, CXI, CX>, CXI: IsPropertyContext, CX: IsPropertyContext>
    : IsSerializablePropertyDefinition<DO, CXI> {
    val dataModel: DM
}
