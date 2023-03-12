package maryk.core.properties

import maryk.core.models.AbstractObjectDataModel
import maryk.core.properties.definitions.wrapper.IsDefinitionWrapper

interface IsBaseModel<DO: Any, P: IsObjectPropertyDefinitions<DO>, in CXI: IsPropertyContext, CX: IsPropertyContext> : IsPropertyDefinitions, Collection<IsDefinitionWrapper<Any, Any, IsPropertyContext, DO>> {
    override val Model: AbstractObjectDataModel<DO, P, CXI, CX>
}
