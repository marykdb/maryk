package maryk.core.query.filters

import maryk.core.objects.SimpleFilterDataModel
import maryk.core.properties.IsPropertyContext
import maryk.core.properties.definitions.PropertyDefinitions
import maryk.core.properties.definitions.wrapper.IsValuePropertyDefinitionWrapper
import maryk.core.properties.references.IsPropertyReference
import maryk.core.query.DataModelPropertyContext
import maryk.json.IsJsonLikeWriter

/** Referenced value should be less than or equal given [value] of type [T] */
infix fun <T: Any> IsPropertyReference<T, IsValuePropertyDefinitionWrapper<T, *, IsPropertyContext, *>>.lessThanEquals(
    value: T
) = LessThanEquals(this, value)

/** Referenced value [reference] should be less than or equal given [value] of type [T] */
data class LessThanEquals<T: Any> internal constructor(
    override val reference: IsPropertyReference<T, IsValuePropertyDefinitionWrapper<T, *, IsPropertyContext, *>>,
    override val value: T
) : IsPropertyComparison<T> {
    override val filterType = FilterType.LessThanEquals

    internal object Properties : PropertyDefinitions<LessThanEquals<*>>() {
        val reference = IsPropertyCheck.addReference(this, LessThanEquals<*>::reference)
        val value = IsPropertyComparison.addValue(this, LessThanEquals<*>::value)
    }

    internal companion object: SimpleFilterDataModel<LessThanEquals<*>>(
        properties = Properties
    ) {
        override fun invoke(map: Map<Int, *>) = LessThanEquals(
            reference = map(0),
            value = map(1)
        )

        override fun writeJson(obj: LessThanEquals<*>, writer: IsJsonLikeWriter, context: DataModelPropertyContext?) {
            writer.writeJsonValues(Properties.reference, obj.reference, Properties.value, obj.value, context)
        }
    }
}
