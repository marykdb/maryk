package maryk.core.properties

import maryk.core.models.IsValuesDataModel
import maryk.core.properties.definitions.IsPropertyDefinition
import maryk.core.properties.references.IsPropertyReference
import maryk.core.values.Values

interface IsValuesPropertyDefinitions: IsTypedPropertyDefinitions<Any>, IsSerializableModel {
    override val Model : IsValuesDataModel<*>
}

/**
 * Validate [values] and get reference from [refGetter] if exception needs to be thrown
 */
internal fun <DM: IsValuesPropertyDefinitions> DM.validate(
    values: Values<DM>,
    refGetter: () -> IsPropertyReference<Values<DM>, IsPropertyDefinition<Values<DM>>, *>? = { null },
) {
    @Suppress("UNCHECKED_CAST")
    (this as IsTypedValuePropertyDefinitions<*, DM>).validate(values, refGetter)
}
