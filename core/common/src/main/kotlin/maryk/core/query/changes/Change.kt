package maryk.core.query.changes

import maryk.core.models.ReferencePairDataModel
import maryk.core.models.ReferenceValuePairsObjectPropertyDefinitions
import maryk.core.objects.ObjectValues
import maryk.core.query.DataModelPropertyContext
import maryk.core.query.pairs.ReferenceValuePair
import maryk.json.IsJsonLikeWriter

/** Defines changes to properties defined by [referenceValuePairs] */
data class Change internal constructor(
    val referenceValuePairs: List<ReferenceValuePair<Any>>
) : IsChange {
    override val changeType = ChangeType.Change

    @Suppress("UNCHECKED_CAST")
    constructor(vararg referenceValuePair: ReferenceValuePair<*>): this(referenceValuePair.toList() as List<ReferenceValuePair<Any>>)

    internal object Properties : ReferenceValuePairsObjectPropertyDefinitions<Any, Change>() {
        override val referenceValuePairs = addReferenceValuePairsDefinition(Change::referenceValuePairs)
    }

    internal companion object: ReferencePairDataModel<Any, Change, Properties>(
        properties = Properties
    ) {
        override fun invoke(map: ObjectValues<Change, Properties>) = Change(
            referenceValuePairs = map(1)
        )

        override fun writeJson(obj: Change, writer: IsJsonLikeWriter, context: DataModelPropertyContext?) {
            writer.writeJsonMapObject(obj.referenceValuePairs, context)
        }
    }
}
