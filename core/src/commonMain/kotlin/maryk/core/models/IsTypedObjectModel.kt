package maryk.core.models

import maryk.core.models.serializers.IsObjectDataModelSerializer
import maryk.core.properties.IsPropertyContext

interface IsTypedObjectModel<DO: Any, DM: IsObjectDataModel<DO>, in CXI : IsPropertyContext, CX : IsPropertyContext>: IsBaseObjectDataModel<DO, DM, CXI, CX>,
    IsTypedObjectDataModel<DO, DM, CX> {
    override val Serializer: IsObjectDataModelSerializer<DO, DM, CXI, CX>
}
