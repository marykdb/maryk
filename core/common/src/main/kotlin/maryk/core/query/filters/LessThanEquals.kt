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

/** Referenced value should be less than or equal given [value] of type [T] */
infix fun <T: Any> IsPropertyReference<T, IsValuePropertyDefinitionWrapper<T, *, IsPropertyContext, *>>.lessThanEquals(
    value: T
) = LessThanEquals(this with value)

/** [referenceValuePairs] should be less than and not equal given value */
data class LessThanEquals internal constructor(
    override val referenceValuePairs: List<ReferenceValuePair<Any>>
) : IsFilter, HasReferenceValuePairs {
    override val filterType = FilterType.LessThanEquals

    @Suppress("UNCHECKED_CAST")
    constructor(vararg referenceValuePair: ReferenceValuePair<out Any>): this(referenceValuePair.toList() as List<ReferenceValuePair<Any>>)

    internal object Properties : ReferenceValuePairsPropertyDefinitions<Any, LessThanEquals>() {
        override val referenceValuePairs = HasReferenceValuePairs.addReferenceValuePairs(
            this, LessThanEquals::referenceValuePairs
        )
    }

    internal companion object: ReferencePairDataModel<Any, LessThanEquals>(
        properties = Properties
    ) {
        override fun invoke(map: Map<Int, *>) = LessThanEquals(
            referenceValuePairs = map(0)
        )

        override fun writeJson(obj: LessThanEquals, writer: IsJsonLikeWriter, context: DataModelPropertyContext?) {
            writer.writeJsonMapObject(obj.referenceValuePairs, context)
        }
    }
}
