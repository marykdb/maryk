package maryk.core.models

import maryk.core.properties.IsRootModel
import maryk.core.properties.IsValuesPropertyDefinitions
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

/** Create a Values object with given [createMap] function */
fun <DM : IsValuesPropertyDefinitions> DM.values(
    context: RequestContext? = null,
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
