package maryk.core.models

import maryk.core.models.definitions.IsValuesDataModelDefinition
import maryk.core.properties.definitions.IsPropertyDefinition
import maryk.core.properties.references.IsPropertyReference
import maryk.core.query.RequestContext
import maryk.core.values.IsValueItems
import maryk.core.values.Values

interface IsValuesDataModel: IsTypedDataModel<Any>, IsStorableDataModel {
    override val Model : IsValuesDataModelDefinition<*>
}

/**
 * Validate [values] and get reference from [refGetter] if exception needs to be thrown
 */
internal fun <DM: IsValuesDataModel> DM.validate(
    values: Values<DM>,
    refGetter: () -> IsPropertyReference<Values<DM>, IsPropertyDefinition<Values<DM>>, *>? = { null },
) {
    @Suppress("UNCHECKED_CAST")
    (this as IsTypedValuesDataModel<DM>).validate(values, refGetter)
}

/** Create a Values object with given [createMap] function */
fun <DM : IsValuesDataModel> DM.values(
    context: RequestContext? = null,
    createMap: DM.() -> IsValueItems
) =
    Values(this, createMap(this), context)
