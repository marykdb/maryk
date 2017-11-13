package maryk.core.properties.references

import maryk.core.extensions.bytes.initIntByVar
import maryk.core.properties.definitions.IsPropertyDefinition
import maryk.core.properties.definitions.ListDefinition
import maryk.core.properties.exceptions.ParseException

/**
 * Reference to a property
 * @param <T> Type of reference
 * @param <D> Definition of property
 */
open class ListReference<T: Any> (
        propertyDefinition: ListDefinition<T, *>,
        parentReference: CanHaveComplexChildReference<*, *, *>?
) : PropertyReference<List<T>, ListDefinition<T, *>, CanHaveComplexChildReference<*, *, *>>(
        propertyDefinition,
        parentReference
), HasEmbeddedPropertyReference<T> {
    override fun getEmbedded(name: String) = when(name[0]) {
        '@' -> ListItemReference(name.substring(1).toInt(), this)
        else -> throw ParseException("Unknown List type $name[0]")
    }

    override fun getEmbeddedRef(reader: () -> Byte): IsPropertyReference<*, IsPropertyDefinition<*>> {
        val index = initIntByVar(reader)
        return when(index) {
            0 -> ListItemReference(initIntByVar(reader), this)
            else -> throw ParseException("Unknown List reference type $index")
        }
    }
}