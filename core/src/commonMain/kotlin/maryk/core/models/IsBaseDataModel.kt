package maryk.core.models

import maryk.core.models.serializers.IsObjectDataModelSerializer
import maryk.core.properties.IsPropertyContext
import maryk.core.properties.definitions.wrapper.IsDefinitionWrapper

interface IsSimpleBaseObjectDataModel<DO: Any, in CXI: IsPropertyContext, CX: IsPropertyContext> : IsObjectDataModel<DO>, Collection<IsDefinitionWrapper<Any, Any, IsPropertyContext, DO>> {
    override val Serializer: IsObjectDataModelSerializer<DO, *, CXI, CX>
}

interface IsBaseObjectDataModel<DO: Any, DM: IsObjectDataModel<DO>, in CXI: IsPropertyContext, CX: IsPropertyContext> :
    IsSimpleBaseObjectDataModel<DO, CXI, CX> {
    override val Serializer: IsObjectDataModelSerializer<DO, DM, CXI, CX>
}
