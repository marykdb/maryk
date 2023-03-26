package maryk.core.properties

import maryk.core.models.IsValuesDataModel
import maryk.core.values.MutableValueItems
import maryk.core.values.ValueItem
import maryk.core.values.Values

abstract class TypedPropertyDefinitions<DM: IsValuesDataModel<P>, P: IsValuesPropertyDefinitions> : PropertyDefinitions() {
    abstract override val Model : DM

    /**
     * Create a new [Values] object with [pairs] and set defaults if [setDefaults] is true
     */
    @Suppress("UNCHECKED_CAST")
    fun create(
        vararg pairs: ValueItem?,
        setDefaults: Boolean = true,
    ) = Values(
        this as P,
        MutableValueItems().apply {
            fillWithPairs(this@TypedPropertyDefinitions, pairs, setDefaults)
        }
    )
}
