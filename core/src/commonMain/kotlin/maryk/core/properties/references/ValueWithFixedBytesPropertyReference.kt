package maryk.core.properties.references

import maryk.core.models.IsValuesDataModel
import maryk.core.properties.definitions.IsFixedBytesEncodable
import maryk.core.properties.definitions.key.IndexKeyPartType
import maryk.core.properties.definitions.wrapper.FixedBytesPropertyDefinitionWrapper
import maryk.core.properties.exceptions.RequiredException
import maryk.core.values.AbstractValues
import maryk.core.values.Values

/**
 * Reference to a value property containing values of type [T] which are of fixed byte length. This can be used inside
 * keys. The property is defined by Property Definition Wrapper [propertyDefinition] of type [D]
 * and referred by PropertyReference of type [P].
 */
open class ValueWithFixedBytesPropertyReference<
    T: Any,
    TO: Any,
    out D : FixedBytesPropertyDefinitionWrapper<T, TO, *, *, *>,
    out P: AnyPropertyReference
> internal constructor(
    propertyDefinition: D,
    parentReference: P?
):
    PropertyReference<T, D, P, AbstractValues<*, *, *>>(propertyDefinition, parentReference),
    IsValuePropertyReference<T, TO, D, P>,
    IsFixedBytesPropertyReference<T>,
    IsFixedBytesEncodable<T> by propertyDefinition
{
    override val byteSize = propertyDefinition.byteSize
    override val indexKeyPartType = IndexKeyPartType.Reference
    override val name = this.propertyDefinition.name

    override fun <DM : IsValuesDataModel<*>> getValue(values: Values<DM, *>) =
        values[this] ?: throw RequiredException(this)

    override fun isForPropertyReference(propertyReference: IsPropertyReference<*, *, *>) = propertyReference == this
}
