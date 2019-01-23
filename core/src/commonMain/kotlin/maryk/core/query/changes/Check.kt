package maryk.core.query.changes

import maryk.core.models.ReferencePairDataModel
import maryk.core.models.ReferenceValuePairsObjectPropertyDefinitions
import maryk.core.query.pairs.ReferenceValuePair
import maryk.core.values.ObjectValues

/** Defines checks to properties defined by [referenceValuePairs] */
data class Check internal constructor(
    val referenceValuePairs: List<ReferenceValuePair<Any>>
) : IsChange {
    override val changeType = ChangeType.Check

    @Suppress("UNCHECKED_CAST")
    constructor(vararg referenceValuePair: ReferenceValuePair<*>): this(referenceValuePair.toList() as List<ReferenceValuePair<Any>>)

    override fun toString() = "Check[${referenceValuePairs.joinToString()}]"

    object Properties : ReferenceValuePairsObjectPropertyDefinitions<Check, ReferenceValuePair<Any>>(
        pairName = "referenceValuePairs",
        pairGetter = Check::referenceValuePairs,
        pairModel = ReferenceValuePair
    )

    companion object: ReferencePairDataModel<Check, Properties, ReferenceValuePair<Any>, Any>(
        properties = Properties,
        pairProperties = ReferenceValuePair.Properties
    ) {
        override fun invoke(values: ObjectValues<Check, Properties>) = Check(
            referenceValuePairs = values(1)
        )
    }
}
