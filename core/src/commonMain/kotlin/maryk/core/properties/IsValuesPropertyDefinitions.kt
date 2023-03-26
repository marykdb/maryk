package maryk.core.properties

import maryk.core.models.IsValuesDataModel

interface IsValuesPropertyDefinitions: IsTypedPropertyDefinitions<Any> {
    override val Model : IsValuesDataModel<*>
}
