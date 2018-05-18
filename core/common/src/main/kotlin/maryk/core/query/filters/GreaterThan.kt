package maryk.core.query.filters

import maryk.core.objects.SimpleFilterDataModel
import maryk.core.properties.IsPropertyContext
import maryk.core.properties.definitions.PropertyDefinitions
import maryk.core.properties.definitions.wrapper.IsValuePropertyDefinitionWrapper
import maryk.core.properties.references.IsPropertyReference
import maryk.core.query.DataModelPropertyContext
import maryk.json.IsJsonLikeWriter

/** Referenced value should be greater than and not equal given [value] of type [T] */
infix fun <T: Any> IsPropertyReference<T, IsValuePropertyDefinitionWrapper<T, *, IsPropertyContext, *>>.greaterThan(
    value: T
) = GreaterThan(this, value)

/** Referenced value [reference] should be greater than and not equal given [value] of type [T] */
data class GreaterThan<T: Any>(
    override val reference: IsPropertyReference<T, IsValuePropertyDefinitionWrapper<T, *, IsPropertyContext, *>>,
    override val value: T
) : IsPropertyComparison<T> {
    override val filterType = FilterType.GreaterThan

    internal object Properties : PropertyDefinitions<GreaterThan<*>>() {
        val reference = IsPropertyCheck.addReference(this, GreaterThan<*>::reference)
        val value = IsPropertyComparison.addValue(this, GreaterThan<*>::value)
    }

    internal companion object: SimpleFilterDataModel<GreaterThan<*>>(
        properties = Properties
    ) {
        override fun invoke(map: Map<Int, *>) = GreaterThan(
            reference = map(0),
            value = map(1)
        )

        override fun writeJson(obj: GreaterThan<*>, writer: IsJsonLikeWriter, context: DataModelPropertyContext?) {
            writer.writeJsonValues(Properties.reference, obj.reference, Properties.value, obj.value, context)
        }
    }
}
