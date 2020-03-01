package maryk.core.query.changes

import maryk.core.models.ReferencePairDataModel
import maryk.core.models.ReferenceValuePairsObjectPropertyDefinitions
import maryk.core.properties.PropertyDefinitions
import maryk.core.properties.graph.RootPropRefGraph
import maryk.core.query.pairs.ReferenceValuePair
import maryk.core.values.ObjectValues

/** Defines changes to properties defined by [referenceValuePairs] */
data class Change internal constructor(
    val referenceValuePairs: List<ReferenceValuePair<Any>>
) : IsChange {
    override val changeType = ChangeType.Change

    @Suppress("UNCHECKED_CAST")
    constructor(vararg referenceValuePair: ReferenceValuePair<*>) : this(referenceValuePair.toList() as List<ReferenceValuePair<Any>>)

    override fun filterWithSelect(select: RootPropRefGraph<out PropertyDefinitions>): Change? {
        val filtered = referenceValuePairs.filter {
            select.contains(it.reference)
        }
        return if (filtered.isEmpty()) null else Change(filtered)
    }

    override fun toString() = "Change[${referenceValuePairs.joinToString()}]"

    object Properties : ReferenceValuePairsObjectPropertyDefinitions<Change, ReferenceValuePair<Any>>(
        pairName = "referenceValuePairs",
        pairGetter = Change::referenceValuePairs,
        pairModel = ReferenceValuePair
    )

    companion object : ReferencePairDataModel<Change, Properties, ReferenceValuePair<Any>, Any, Any>(
        Properties,
        ReferenceValuePair.Properties
    ) {
        override fun invoke(values: ObjectValues<Change, Properties>) = Change(
            referenceValuePairs = values(1u)
        )
    }
}
