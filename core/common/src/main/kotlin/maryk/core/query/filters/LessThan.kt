package maryk.core.query.filters

import maryk.core.objects.SimpleFilterDataModel
import maryk.core.properties.IsPropertyContext
import maryk.core.properties.definitions.PropertyDefinitions
import maryk.core.properties.definitions.wrapper.IsValuePropertyDefinitionWrapper
import maryk.core.properties.references.IsPropertyReference
import maryk.core.query.DataModelPropertyContext
import maryk.json.IsJsonLikeWriter

/** Referenced value should be less than and not equalgiven [value] of type [T] */
infix fun <T: Any> IsPropertyReference<T, IsValuePropertyDefinitionWrapper<T, *, IsPropertyContext, *>>.lessThan(
    value: T
) = LessThan(this, value)

/** Referenced value [reference] should be less than and not equal given [value] of type [T] */
data class LessThan<T: Any> internal constructor(
    override val reference: IsPropertyReference<T, IsValuePropertyDefinitionWrapper<T, *, IsPropertyContext, *>>,
    override val value: T
) : IsPropertyComparison<T> {
    override val filterType = FilterType.LessThan

    internal object Properties : PropertyDefinitions<LessThan<*>>() {
        val reference = IsPropertyCheck.addReference(this, LessThan<*>::reference)
        val value = IsPropertyComparison.addValue(this, LessThan<*>::value)
    }

    internal companion object: SimpleFilterDataModel<LessThan<*>>(
        properties = Properties
    ) {
        override fun invoke(map: Map<Int, *>) = LessThan(
            reference = map(0),
            value = map(1)
        )

        override fun writeJson(obj: LessThan<*>, writer: IsJsonLikeWriter, context: DataModelPropertyContext?) {
            writer.writeJsonValues(Properties.reference, obj.reference, Properties.value, obj.value, context)
        }
    }
}
