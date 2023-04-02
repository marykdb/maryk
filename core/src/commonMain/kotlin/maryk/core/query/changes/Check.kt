package maryk.core.query.changes

import maryk.core.models.IsRootDataModel
import maryk.core.models.ReferenceValuePairsDataModel
import maryk.core.properties.graph.RootPropRefGraph
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

    override fun filterWithSelect(select: RootPropRefGraph<out IsRootDataModel>): Check? {
        val filtered = referenceValuePairs.filter {
            select.contains(it.reference)
        }
        return if (filtered.isEmpty()) null else Check(filtered)
    }

    override fun changeValues(objectChanger: (IsPropertyReferenceForValues<*, *, *, *>, (Any?, Any?) -> Any?) -> Unit) {
        // Changes nothing so do nothing
    }

    override fun toString() = "Check[${referenceValuePairs.joinToString()}]"

    companion object : ReferenceValuePairsDataModel<Check, Companion, ReferenceValuePair<Any>, Any, Any>(
        pairGetter = Check::referenceValuePairs,
        pairModel = ReferenceValuePair,
    ) {
        override fun invoke(values: ObjectValues<Check, Companion>) = Check(
            referenceValuePairs = values(1u)
        )
    }
}
