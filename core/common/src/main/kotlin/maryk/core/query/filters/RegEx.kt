package maryk.core.query.filters

import maryk.core.models.ReferencePairDataModel
import maryk.core.models.ReferenceValuePairsPropertyDefinitions
import maryk.core.objects.DataObjectMap
import maryk.core.query.DataModelPropertyContext
import maryk.core.query.pairs.ReferenceValuePair
import maryk.json.IsJsonLikeWriter

/** Referenced values in [referenceValuePairs] should match with regular expressions */
data class RegEx internal constructor(
    val referenceValuePairs: List<ReferenceValuePair<String>>
) : IsFilter {
    override val filterType = FilterType.RegEx

    constructor(vararg referenceValuePair: ReferenceValuePair<String>): this(referenceValuePair.toList())

    internal object Properties : ReferenceValuePairsPropertyDefinitions<String, RegEx>() {
        override val referenceValuePairs = addReferenceValuePairsDefinition(RegEx::referenceValuePairs)
    }

    internal companion object: ReferencePairDataModel<String, RegEx>(
        properties = Properties
    ) {
        override fun invoke(map: DataObjectMap<RegEx>) = RegEx(
            referenceValuePairs = map(0)
        )

        override fun writeJson(obj: RegEx, writer: IsJsonLikeWriter, context: DataModelPropertyContext?) {
            writer.writeJsonMapObject(obj.referenceValuePairs, context)
        }
    }
}
