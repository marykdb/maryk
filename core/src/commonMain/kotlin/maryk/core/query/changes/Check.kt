package maryk.core.query.changes

import maryk.core.models.ReferencePairDataModel
import maryk.core.models.ReferenceValuePairsObjectPropertyDefinitions
import maryk.core.objects.ObjectValues
import maryk.core.query.RequestContext
import maryk.core.query.pairs.ReferenceValuePair
import maryk.json.IsJsonLikeWriter

/** Defines checks to properties defined by [referenceValuePairs] */
data class Check internal constructor(
    val referenceValuePairs: List<ReferenceValuePair<Any>>
) : IsChange {
    override val changeType = ChangeType.Check

    @Suppress("UNCHECKED_CAST")
    constructor(vararg referenceValuePair: ReferenceValuePair<*>): this(referenceValuePair.toList() as List<ReferenceValuePair<Any>>)

    object Properties : ReferenceValuePairsObjectPropertyDefinitions<Any, Check>() {
        override val referenceValuePairs = addReferenceValuePairsDefinition(Check::referenceValuePairs)
    }

    companion object: ReferencePairDataModel<Any, Check, Properties>(
        properties = Properties
    ) {
        override fun invoke(map: ObjectValues<Check, Properties>) = Check(
            referenceValuePairs = map(1)
        )

        override fun writeJson(obj: Check, writer: IsJsonLikeWriter, context: RequestContext?) {
            writer.writeJsonMapObject(obj.referenceValuePairs, context)
        }
    }
}
