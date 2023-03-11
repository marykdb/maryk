package maryk.core.properties

import maryk.core.models.AbstractObjectDataModel

interface IsBaseModel<DO: Any, P: IsObjectPropertyDefinitions<DO>, in CXI: IsPropertyContext, CX: IsPropertyContext> : IsPropertyDefinitions {
    override val Model: AbstractObjectDataModel<DO, P, CXI, CX>
}
