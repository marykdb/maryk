package maryk.core.query.filters

import maryk.core.objects.ReferencePairDataModel
import maryk.core.objects.ReferenceValuePairsPropertyDefinitions
import maryk.core.properties.IsPropertyContext
import maryk.core.properties.definitions.wrapper.IsValuePropertyDefinitionWrapper
import maryk.core.properties.references.IsPropertyReference
import maryk.core.query.DataModelPropertyContext
import maryk.core.query.pairs.ReferenceValuePair
import maryk.core.query.pairs.with
import maryk.json.IsJsonLikeWriter

/** Compares given regular expression [regEx] against referenced property */
infix fun IsPropertyReference<String, IsValuePropertyDefinitionWrapper<String, *, IsPropertyContext, *>>.matchesRegEx(
    regEx: String
) = RegEx(this with regEx)

/** Referenced values in [referenceValuePairs] should match with regular expressions */
data class RegEx(
    override val referenceValuePairs: List<ReferenceValuePair<String>>
) : IsFilter, HasReferenceValuePairs {
    override val filterType = FilterType.RegEx

    constructor(vararg referenceValuePair: ReferenceValuePair<String>): this(referenceValuePair.toList())

    internal object Properties : ReferenceValuePairsPropertyDefinitions<String, RegEx>() {
        override val referenceValuePairs = HasReferenceValuePairs.addReferenceValuePairs(
            this, RegEx::referenceValuePairs
        )
    }

    internal companion object: ReferencePairDataModel<String, RegEx>(
        properties = Properties
    ) {
        override fun invoke(map: Map<Int, *>) = RegEx(
            referenceValuePairs = map(0)
        )

        override fun writeJson(obj: RegEx, writer: IsJsonLikeWriter, context: DataModelPropertyContext?) {
            writer.writeJsonMapObject(obj.referenceValuePairs, context)
        }
    }
}
