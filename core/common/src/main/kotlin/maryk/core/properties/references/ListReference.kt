package maryk.core.properties.references

import maryk.core.extensions.bytes.initIntByVar
import maryk.core.properties.IsPropertyContext
import maryk.core.properties.definitions.IsPropertyDefinition
import maryk.core.properties.definitions.wrapper.ListPropertyDefinitionWrapper
import maryk.lib.exceptions.ParseException

/** Reference to a List property of type [T] and context [CX] */
open class ListReference<T: Any, CX: IsPropertyContext> internal constructor(
    propertyDefinition: ListPropertyDefinitionWrapper<T, Any, CX, *>,
    parentReference: CanHaveComplexChildReference<*, *, *>?
) : ValuePropertyReference<List<T>, List<Any>, ListPropertyDefinitionWrapper<T, Any, CX, *>, CanHaveComplexChildReference<*, *, *>>(
    propertyDefinition,
    parentReference
), HasEmbeddedPropertyReference<T> {
    override fun getEmbedded(name: String) = when(name[0]) {
        '@' -> ListItemReference(name.substring(1).toInt(), propertyDefinition.definition, this)
        else -> throw ParseException("Unknown List type $name[0]")
    }

    override fun getEmbeddedRef(reader: () -> Byte): IsPropertyReference<*, IsPropertyDefinition<*>> {
        val index = initIntByVar(reader)
        return when(index) {
            0 -> ListItemReference(initIntByVar(reader), propertyDefinition.definition, this)
            else -> throw ParseException("Unknown List reference type $index")
        }
    }
}
