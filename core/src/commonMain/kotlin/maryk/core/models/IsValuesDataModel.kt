package maryk.core.models

import maryk.core.properties.IsRootModel
import maryk.core.properties.IsValuesPropertyDefinitions
import maryk.core.properties.definitions.IsPropertyDefinition
import maryk.core.properties.exceptions.ValidationUmbrellaException
import maryk.core.properties.references.IsPropertyReference
import maryk.core.query.RequestContext
import maryk.core.query.changes.IsChange
import maryk.core.values.IsValueItems
import maryk.core.values.MutableValueItems
import maryk.core.values.ValueItems
import maryk.core.values.Values

interface IsValuesDataModel<P : IsValuesPropertyDefinitions> : IsNamedDataModel<P> {
    val reservedIndices: List<UInt>?
    val reservedNames: List<String>?

    /** Check the property values */
    fun check() {
        this.reservedIndices?.let { reservedIndices ->
            this.properties.forEach { property ->
                require(!reservedIndices.contains(property.index)) {
                    "Model $name has ${property.index} defined in option ${property.name} while it is reserved"
                }
            }
        }
        this.reservedNames?.let { reservedNames ->
            this.properties.forEach { case ->
                require(!reservedNames.contains(case.name)) {
                    "Model $name has a reserved name defined ${case.name}"
                }
            }
        }
    }
}

/** A DataModel which holds properties and can be validated */
interface IsTypedValuesDataModel<DM : IsValuesDataModel<P>, P : IsValuesPropertyDefinitions> :
    IsDataModelWithValues<Any, P, Values<P>>,
    IsValuesDataModel<P> {
    /**
     * Validate a [map] with values and get reference from [refGetter] if exception needs to be thrown
     * @throws ValidationUmbrellaException if input was invalid
     */
    fun validate(
        values: Values<P>,
        refGetter: () -> IsPropertyReference<Values<P>, IsPropertyDefinition<Values<P>>, *>? = { null }
    )

    /** Create a ObjectValues with given [createValues] function */
    @Suppress("UNCHECKED_CAST")
    override fun values(context: RequestContext?, createValues: P.() -> IsValueItems) =
        Values(this.properties, createValues(this.properties), context)
}

/** Create a Values object with given [createMap] function */
fun <DM : IsValuesPropertyDefinitions> DM.values(
    context: RequestContext?,
    createMap: DM.() -> IsValueItems
) =
    Values(this, createMap(this), context)

/** Create a Values object with given [changes] */
fun <DM : IsRootModel> DM.fromChanges(
    context: RequestContext?,
    changes: List<IsChange>
) = if (changes.isEmpty()) {
    Values(this, ValueItems(), context)
} else {
    val valueItemsToChange = MutableValueItems(mutableListOf())

    for (change in changes) {
        change.changeValues { ref, valueChanger ->
            valueItemsToChange.copyFromOriginalAndChange(null, ref.index, valueChanger)
        }
    }

    Values(this, valueItemsToChange, context)
}
