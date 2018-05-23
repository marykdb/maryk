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

/** Referenced value should be greater than and not equal given [value] of type [T] */
infix fun <T: Any> IsPropertyReference<T, IsValuePropertyDefinitionWrapper<T, *, IsPropertyContext, *>>.greaterThan(
    value: T
) = GreaterThan(this with value)

/** Referenced values in [referenceValuePairs] should be greater than and not equal given value */
data class GreaterThan(
    val referenceValuePairs: List<ReferenceValuePair<Any>>
) : IsFilter {
    override val filterType = FilterType.GreaterThan

    @Suppress("UNCHECKED_CAST")
    constructor(vararg referenceValuePair: ReferenceValuePair<*>): this(referenceValuePair.toList() as List<ReferenceValuePair<Any>>)

    internal object Properties : ReferenceValuePairsPropertyDefinitions<Any, GreaterThan>() {
        override val referenceValuePairs = ReferenceValuePair.addReferenceValuePairsDefinition(
            this, GreaterThan::referenceValuePairs
        )
    }

    internal companion object: ReferencePairDataModel<Any, GreaterThan>(
        properties = Properties
    ) {
        override fun invoke(map: Map<Int, *>) = GreaterThan(
            referenceValuePairs = map(0)
        )

        override fun writeJson(obj: GreaterThan, writer: IsJsonLikeWriter, context: DataModelPropertyContext?) {
            writer.writeJsonMapObject(obj.referenceValuePairs, context)
        }
    }
}
