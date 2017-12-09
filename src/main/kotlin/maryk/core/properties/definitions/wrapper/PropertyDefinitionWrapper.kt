package maryk.core.properties.definitions.wrapper

import maryk.core.properties.IsPropertyContext
import maryk.core.properties.definitions.IsSerializableFlexBytesEncodable
import maryk.core.properties.references.IsPropertyReference
import maryk.core.properties.references.ValuePropertyReference

/** Wrapper for a Property Definition to contain the context on how it relates to DataObject
 * @param index: of definition to encode into protobuf
 * @param name: of definition to display in human readable format
 * @param definition: to be wrapped for DataObject
 * @param getter: to get property value on a DataObject
 *
 * @param T: value type of property
 * @param CX: Context type for property
 * @param D: Type of Definition contained
 * @param DO: Type of DataObject which contains this property
 */
data class PropertyDefinitionWrapper<T: Any, CX: IsPropertyContext, D: IsSerializableFlexBytesEncodable<T, CX>, DO: Any>(
        override val index: Int,
        override val name: String,
        override val definition: D,
        override val getter: (DO) -> T?
) : IsSerializableFlexBytesEncodable<T, CX> by definition, IsValuePropertyDefinitionWrapper<T, CX, DO> {
    override fun getRef(parentRef: IsPropertyReference<*, *>?)
            = ValuePropertyReference(this, parentRef)
}
