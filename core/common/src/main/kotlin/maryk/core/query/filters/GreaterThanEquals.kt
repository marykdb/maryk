package maryk.core.query.filters

import maryk.core.objects.SimpleFilterDataModel
import maryk.core.properties.IsPropertyContext
import maryk.core.properties.definitions.PropertyDefinitions
import maryk.core.properties.definitions.wrapper.IsValuePropertyDefinitionWrapper
import maryk.core.properties.references.IsPropertyReference
import maryk.core.query.DataModelPropertyContext
import maryk.json.IsJsonLikeWriter

/** Referenced value should be greater than or equal given [value] of type [T] */
infix fun <T: Any> IsPropertyReference<T, IsValuePropertyDefinitionWrapper<T, *, IsPropertyContext, *>>.greaterThanEquals(
    value: T
) = GreaterThanEquals(this, value)

/** Referenced value [reference] should be greater than or equal given [value] of type [T] */
data class GreaterThanEquals<T: Any>(
    override val reference: IsPropertyReference<T, IsValuePropertyDefinitionWrapper<T, *, IsPropertyContext, *>>,
    override val value: T
) : IsPropertyComparison<T> {
    override val filterType = FilterType.GreaterThanEquals

    internal object Properties : PropertyDefinitions<GreaterThanEquals<*>>() {
        val reference = IsPropertyCheck.addReference(this, GreaterThanEquals<*>::reference)
        val value = IsPropertyComparison.addValue(this, GreaterThanEquals<*>::value)
    }

    internal companion object: SimpleFilterDataModel<GreaterThanEquals<*>>(
        properties = Properties
    ) {
        override fun invoke(map: Map<Int, *>) = GreaterThanEquals(
            reference = map(0),
            value = map(1)
        )

        override fun writeJson(obj: GreaterThanEquals<*>, writer: IsJsonLikeWriter, context: DataModelPropertyContext?) {
            writer.writeJsonValues(Properties.reference, obj.reference, Properties.value, obj.value, context)
        }
    }
}
