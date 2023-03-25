package maryk.core.properties

import maryk.core.models.AbstractObjectDataModel
import maryk.core.properties.definitions.wrapper.IsDefinitionWrapper

interface IsSimpleBaseModel<DO: Any, in CXI: IsPropertyContext, CX: IsPropertyContext> : IsObjectPropertyDefinitions<DO>, Collection<IsDefinitionWrapper<Any, Any, IsPropertyContext, DO>> {
    override val Model: AbstractObjectDataModel<DO, *, CXI, CX>
}

interface IsBaseModel<DO: Any, P: IsObjectPropertyDefinitions<DO>, in CXI: IsPropertyContext, CX: IsPropertyContext> : IsObjectPropertyDefinitions<DO>, Collection<IsDefinitionWrapper<Any, Any, IsPropertyContext, DO>>, IsSimpleBaseModel<DO, CXI, CX> {
    override val Model: AbstractObjectDataModel<DO, P, CXI, CX>
}