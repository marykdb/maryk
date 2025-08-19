package maryk.core.models

import maryk.core.models.definitions.IsValuesDataModelDefinition
import maryk.core.properties.definitions.IsPropertyDefinition
import maryk.core.properties.references.IsPropertyReference
import maryk.core.query.RequestContext
import maryk.core.values.IsValueItems
import maryk.core.values.Values

interface IsValuesDataModel: IsTypedDataModel<Any>, IsStorableDataModel<Any> {
    override val Meta : IsValuesDataModelDefinition

    /**
     * Checks the model if there are no conflicting values.
     * It at the moment checks the reserved indexes and names of models to see if those are not used in the model.
     */
    fun checkModel() {
        this.Meta.reservedIndices?.let { reservedIndices ->
            this.forEach { property ->
                require(!reservedIndices.contains(property.index)) {
                    "Model ${Meta.name} has ${property.index} defined in option ${property.name} while it is reserved"
                }
            }
        }
        this.Meta.reservedNames?.let { reservedNames ->
            this.forEach { case ->
                require(!reservedNames.contains(case.name)) {
                    "Model ${Meta.name} has a reserved name defined ${case.name}"
                }
            }
        }
    }
}

/**
 * Validate [values] and get reference from [refGetter] if exception needs to be thrown
 */
internal fun <DM: IsValuesDataModel> DM.validate(
    values: Values<DM>,
    refGetter: () -> IsPropertyReference<Values<DM>, IsPropertyDefinition<Values<DM>>, *>? = { null },
    failOnUnknownProperties: Boolean = true,
    failOnMissingRequiredValues: Boolean = true,
) {
    @Suppress("UNCHECKED_CAST")
    (this as IsTypedValuesDataModel<DM>).validate(
        values = values,
        refGetter = refGetter,
        failOnUnknownProperties = failOnUnknownProperties,
        failOnMissingRequiredValues = failOnMissingRequiredValues
    )
}

/** Create a Values object with given [createMap] function */
fun <DM : IsValuesDataModel> DM.values(
    context: RequestContext? = null,
    createMap: DM.() -> IsValueItems
) =
    Values(this, createMap(this), context)
