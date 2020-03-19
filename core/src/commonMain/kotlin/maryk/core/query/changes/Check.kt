package maryk.core.query.changes

import maryk.core.models.ReferencePairDataModel
import maryk.core.models.ReferenceValuePairsObjectPropertyDefinitions
import maryk.core.properties.PropertyDefinitions
import maryk.core.properties.graph.RootPropRefGraph
import maryk.core.properties.references.AnyPropertyReference
import maryk.core.properties.references.IsPropertyReferenceForValues
import maryk.core.query.pairs.ReferenceValuePair
import maryk.core.values.ObjectValues

/** Defines checks to properties defined by [referenceValuePairs] */
data class Check internal constructor(
    val referenceValuePairs: List<ReferenceValuePair<Any>>
) : IsChange {
    override val changeType = ChangeType.Check

    @Suppress("UNCHECKED_CAST")
    constructor(vararg referenceValuePair: ReferenceValuePair<*>) : this(referenceValuePair.toList() as List<ReferenceValuePair<Any>>)

    override fun filterWithSelect(select: RootPropRefGraph<out PropertyDefinitions>): Check? {
        val filtered = referenceValuePairs.filter {
            select.contains(it.reference)
        }
        return if (filtered.isEmpty()) null else Check(filtered)
    }

    override fun changeValues(objectChanger: (IsPropertyReferenceForValues<*, *, *, *>, (Any?, Any?) -> Any?) -> Unit) {
        // Changes nothing so do nothing
    }

    override fun toString() = "Check[${referenceValuePairs.joinToString()}]"

    object Properties : ReferenceValuePairsObjectPropertyDefinitions<Check, ReferenceValuePair<Any>>(
        pairName = "referenceValuePairs",
        pairGetter = Check::referenceValuePairs,
        pairModel = ReferenceValuePair
    )

    companion object : ReferencePairDataModel<Check, Properties, ReferenceValuePair<Any>, Any, Any>(
        properties = Properties,
        pairProperties = ReferenceValuePair.Properties
    ) {
        override fun invoke(values: ObjectValues<Check, Properties>) = Check(
            referenceValuePairs = values(1u)
        )
    }
}
