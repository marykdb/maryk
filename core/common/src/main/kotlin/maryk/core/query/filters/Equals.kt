package maryk.core.query.filters

import maryk.core.models.ReferencePairDataModel
import maryk.core.models.ReferenceValuePairsObjectPropertyDefinitions
import maryk.core.objects.ObjectValues
import maryk.core.query.DataModelPropertyContext
import maryk.core.query.pairs.ReferenceValuePair
import maryk.json.IsJsonLikeWriter

/** Referenced values in [referenceValuePairs] should be equal given value */
data class Equals internal constructor(
    val referenceValuePairs: List<ReferenceValuePair<Any>>
) : IsFilter {
    override val filterType = FilterType.Equals

    @Suppress("UNCHECKED_CAST")
    constructor(vararg referenceValuePair: ReferenceValuePair<*>): this(referenceValuePair.toList() as List<ReferenceValuePair<Any>>)

    object Properties: ReferenceValuePairsObjectPropertyDefinitions<Any, Equals>() {
        override val referenceValuePairs = addReferenceValuePairsDefinition(Equals::referenceValuePairs)
    }

    companion object: ReferencePairDataModel<Any, Equals, Properties>(
        properties = Properties
    ) {
        override fun invoke(map: ObjectValues<Equals, Properties>) = Equals(
            referenceValuePairs = map(1)
        )

        override fun writeJson(obj: Equals, writer: IsJsonLikeWriter, context: DataModelPropertyContext?) {
            writer.writeJsonMapObject(obj.referenceValuePairs, context)
        }
    }
}
