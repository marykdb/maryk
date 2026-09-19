package maryk.core.models

import maryk.core.properties.definitions.IsPropertyDefinition
import maryk.core.properties.references.IsPropertyReference
import maryk.core.values.Values

interface IsTypedValuesDataModel<DM: IsValuesDataModel>: IsValuesDataModel {
    /**
     * Validate a [map] with values and get reference from [refGetter] if exception needs to be thrown
     * @throws maryk.core.properties.exceptions.ValidationUmbrellaException if input was invalid
     */
    fun validate(
        values: Values<DM>,
        refGetter: () -> IsPropertyReference<Values<DM>, IsPropertyDefinition<Values<DM>>, *>? = { null },
        failOnUnknownProperties: Boolean = true,
        failOnMissingRequiredValues: Boolean = true,
    )
}
