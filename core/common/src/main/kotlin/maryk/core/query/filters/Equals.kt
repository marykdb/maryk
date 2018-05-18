package maryk.core.query.filters

import maryk.core.objects.SimpleFilterDataModel
import maryk.core.properties.IsPropertyContext
import maryk.core.properties.definitions.PropertyDefinitions
import maryk.core.properties.definitions.wrapper.IsValuePropertyDefinitionWrapper
import maryk.core.properties.references.IsPropertyReference
import maryk.core.query.DataModelPropertyContext
import maryk.json.IsJsonLikeWriter

/** Compares given [value] of type [T] against referenced value */
infix fun <T: Any> IsPropertyReference<T, IsValuePropertyDefinitionWrapper<T, *, IsPropertyContext, *>>.equals(
    value: T
) = Equals(this, value)

/** Compares given [value] of type [T] against referenced value [reference] */
data class Equals<T: Any> internal constructor(
    override val reference: IsPropertyReference<T, IsValuePropertyDefinitionWrapper<T, *, IsPropertyContext, *>>,
    override val value: T
) : IsPropertyComparison<T> {
    override val filterType = FilterType.Equals

    internal object Properties: PropertyDefinitions<Equals<*>>() {
        val reference = IsPropertyCheck.addReference(this, Equals<*>::reference)
        val value = IsPropertyComparison.addValue(this, Equals<*>::value)
    }

    internal companion object: SimpleFilterDataModel<Equals<*>>(
        properties = Properties
    ) {
        override fun invoke(map: Map<Int, *>) = Equals(
            reference = map(0),
            value = map(1)
        )

        override fun writeJson(obj: Equals<*>, writer: IsJsonLikeWriter, context: DataModelPropertyContext?) {
            writer.writeJsonValues(
                Properties.reference,
                obj.reference,
                Properties.value,
                obj.value,
                context
            )
        }
    }
}
