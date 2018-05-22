package maryk.core.query.filters

import maryk.core.objects.ReferencePairDataModel
import maryk.core.objects.ReferenceValuePairsPropertyDefinitions
import maryk.core.properties.IsPropertyContext
import maryk.core.properties.definitions.wrapper.IsValuePropertyDefinitionWrapper
import maryk.core.properties.references.IsPropertyReference
import maryk.core.query.DataModelPropertyContext
import maryk.core.query.pairs.ReferenceValuePair
import maryk.json.IsJsonLikeWriter

/** Compares given [value] of type [T] against referenced value */
infix fun <T: Any> IsPropertyReference<T, IsValuePropertyDefinitionWrapper<T, *, IsPropertyContext, *>>.equals(
    value: T
) = Equals(ReferenceValuePair(this, value))

/** Referenced values in [referenceValuePairs] should be equal given value */
data class Equals internal constructor(
    override val referenceValuePairs: List<ReferenceValuePair<Any>>
) : IsFilter, HasReferenceValuePairs {
    override val filterType = FilterType.Equals

    @Suppress("UNCHECKED_CAST")
    constructor(vararg referenceValuePair: ReferenceValuePair<*>): this(referenceValuePair.toList() as List<ReferenceValuePair<Any>>)

    internal object Properties: ReferenceValuePairsPropertyDefinitions<Any, Equals>() {
        override val referenceValuePairs = HasReferenceValuePairs.addReferenceValuePairs(
            this, Equals::referenceValuePairs
        )
    }

    internal companion object: ReferencePairDataModel<Any, Equals>(
        properties = Properties
    ) {
        override fun invoke(map: Map<Int, *>) = Equals(
            referenceValuePairs = map(0)
        )

        override fun writeJson(obj: Equals, writer: IsJsonLikeWriter, context: DataModelPropertyContext?) {
            writer.writeJsonMapObject(obj.referenceValuePairs, context)
        }
    }
}
