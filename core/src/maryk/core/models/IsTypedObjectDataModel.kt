package maryk.core.models

import maryk.core.models.serializers.IsObjectDataModelSerializer
import maryk.core.properties.IsPropertyContext
import maryk.core.values.ObjectValues

interface IsTypedObjectDataModel<DO: Any, DM: IsObjectDataModel<DO>, in CXI : IsPropertyContext, CX : IsPropertyContext>:
    IsObjectDataModel<DO> {
    override val Serializer: IsObjectDataModelSerializer<DO, DM, CXI, CX>

    operator fun invoke(values: ObjectValues<DO, DM>): DO
}
