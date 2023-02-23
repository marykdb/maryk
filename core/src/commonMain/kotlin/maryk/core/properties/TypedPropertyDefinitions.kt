package maryk.core.properties

import maryk.core.models.IsValuesDataModel

@Suppress("PropertyName")
abstract class TypedPropertyDefinitions<DM: IsValuesDataModel<P>, P: IsValuesPropertyDefinitions> : PropertyDefinitions() {
    abstract override val Model : DM
}
