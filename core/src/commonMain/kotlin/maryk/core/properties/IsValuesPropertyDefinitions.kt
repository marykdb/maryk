package maryk.core.properties

import maryk.core.models.IsValuesDataModel

interface IsValuesPropertyDefinitions: IsObjectPropertyDefinitions<Any> {
    override val Model : IsValuesDataModel<*>
}
