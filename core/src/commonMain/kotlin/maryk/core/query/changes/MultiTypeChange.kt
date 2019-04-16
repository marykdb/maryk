package maryk.core.query.changes

import maryk.core.models.QueryDataModel
import maryk.core.models.ReferencePairDataModel
import maryk.core.models.ReferenceValuePairsObjectPropertyDefinitions
import maryk.core.properties.enum.IndexedEnum
import maryk.core.query.pairs.ReferenceTypePair
import maryk.core.values.ObjectValues

/** Defines a change in type for complex multi type value */
data class MultiTypeChange internal constructor(
    val referenceTypePairs: List<ReferenceTypePair<*>>
) : IsChange {
    override val changeType = ChangeType.TypeChange

    constructor(vararg referenceTypePair: ReferenceTypePair<*>) : this(referenceTypePair.toList())

    object Properties : ReferenceValuePairsObjectPropertyDefinitions<MultiTypeChange, ReferenceTypePair<*>>(
        pairName = "referenceValuePairs",
        pairGetter = MultiTypeChange::referenceTypePairs,
        pairModel = ReferenceTypePair as QueryDataModel<ReferenceTypePair<*>, *>
    )

    companion object : ReferencePairDataModel<MultiTypeChange, Properties, ReferenceTypePair<*>, IndexedEnum>(
        properties = Properties,
        pairProperties = ReferenceTypePair.Properties
    ) {
        override fun invoke(values: ObjectValues<MultiTypeChange, Properties>) = MultiTypeChange(
            referenceTypePairs = values(1)
        )
    }
}
