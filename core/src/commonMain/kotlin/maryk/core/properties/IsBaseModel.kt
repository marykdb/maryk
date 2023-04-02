package maryk.core.properties

import maryk.core.models.IsDataModel
import maryk.core.models.serializers.IsObjectDataModelSerializer
import maryk.core.properties.definitions.wrapper.IsDefinitionWrapper

interface IsSimpleBaseModel<DO: Any, in CXI: IsPropertyContext, CX: IsPropertyContext> : IsObjectPropertyDefinitions<DO>, Collection<IsDefinitionWrapper<Any, Any, IsPropertyContext, DO>> {
    override val Serializer: IsObjectDataModelSerializer<DO, *, CXI, CX>
}

interface IsBaseModel<DO: Any, P: IsObjectPropertyDefinitions<DO>, in CXI: IsPropertyContext, CX: IsPropertyContext> : IsSimpleBaseModel<DO, CXI, CX> {
    override val Serializer: IsObjectDataModelSerializer<DO, P, CXI, CX>
    override val Model: IsDataModel<P>
}
