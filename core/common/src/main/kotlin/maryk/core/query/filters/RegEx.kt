package maryk.core.query.filters

import maryk.core.models.ReferencePairDataModel
import maryk.core.models.ReferenceValuePairsObjectPropertyDefinitions
import maryk.core.objects.ObjectValues
import maryk.core.query.DataModelPropertyContext
import maryk.core.query.pairs.ReferenceValuePair
import maryk.json.IsJsonLikeWriter

/** Referenced values in [referenceValuePairs] should match with regular expressions */
data class RegEx internal constructor(
    val referenceValuePairs: List<ReferenceValuePair<String>>
) : IsFilter {
    override val filterType = FilterType.RegEx

    constructor(vararg referenceValuePair: ReferenceValuePair<String>): this(referenceValuePair.toList())

    internal object Properties : ReferenceValuePairsObjectPropertyDefinitions<String, RegEx>() {
        override val referenceValuePairs = addReferenceValuePairsDefinition(RegEx::referenceValuePairs)
    }

    internal companion object: ReferencePairDataModel<String, RegEx, Properties>(
        properties = Properties
    ) {
        override fun invoke(map: ObjectValues<RegEx, Properties>) = RegEx(
            referenceValuePairs = map(0)
        )

        override fun writeJson(obj: RegEx, writer: IsJsonLikeWriter, context: DataModelPropertyContext?) {
            writer.writeJsonMapObject(obj.referenceValuePairs, context)
        }
    }
}
