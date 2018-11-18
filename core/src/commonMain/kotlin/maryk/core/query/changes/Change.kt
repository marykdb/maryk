package maryk.core.query.changes

import maryk.core.models.ReferencePairDataModel
import maryk.core.models.ReferenceValuePairsObjectPropertyDefinitions
import maryk.core.values.ObjectValues
import maryk.core.query.RequestContext
import maryk.core.query.pairs.ReferenceValuePair
import maryk.json.IsJsonLikeWriter

/** Defines changes to properties defined by [referenceValuePairs] */
data class Change internal constructor(
    val referenceValuePairs: List<ReferenceValuePair<Any>>
) : IsChange {
    override val changeType = ChangeType.Change

    @Suppress("UNCHECKED_CAST")
    constructor(vararg referenceValuePair: ReferenceValuePair<*>): this(referenceValuePair.toList() as List<ReferenceValuePair<Any>>)

    object Properties : ReferenceValuePairsObjectPropertyDefinitions<Any, Change>() {
        override val referenceValuePairs = addReferenceValuePairsDefinition(Change::referenceValuePairs)
    }

    companion object: ReferencePairDataModel<Any, Change, Properties>(
        properties = Properties
    ) {
        override fun invoke(values: ObjectValues<Change, Properties>) = Change(
            referenceValuePairs = values(1)
        )

        override fun writeJson(obj: Change, writer: IsJsonLikeWriter, context: RequestContext?) {
            writer.writeJsonMapObject(obj.referenceValuePairs, context)
        }
    }
}
