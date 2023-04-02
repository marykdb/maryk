package maryk.core.properties

import maryk.core.models.definitions.IsValuesDataModel
import maryk.core.properties.definitions.IsPropertyDefinition
import maryk.core.properties.references.IsPropertyReference
import maryk.core.query.RequestContext
import maryk.core.values.IsValueItems
import maryk.core.values.Values

interface IsValuesPropertyDefinitions: IsTypedPropertyDefinitions<Any>, IsStorableModel {
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
    (this as IsTypedValuesModel<*, DM>).validate(values, refGetter)
}

/** Create a Values object with given [createMap] function */
fun <DM : IsValuesPropertyDefinitions> DM.values(
    context: RequestContext? = null,
    createMap: DM.() -> IsValueItems
) =
    Values(this, createMap(this), context)
