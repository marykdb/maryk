package maryk.core.properties

import maryk.core.models.IsNamedDataModel

interface IsSerializableModel: IsPropertyDefinitions {
    override val Model: IsNamedDataModel<*>
}
