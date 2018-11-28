@file:Suppress("EXPERIMENTAL_API_USAGE")

package maryk.core.properties.references

import maryk.core.extensions.bytes.initIntByVar
import maryk.core.extensions.bytes.initUInt
import maryk.core.extensions.bytes.writeVarIntWithExtraInfo
import maryk.core.properties.IsPropertyContext
import maryk.core.properties.definitions.IsPropertyDefinition
import maryk.core.properties.definitions.ListDefinition
import maryk.core.properties.definitions.wrapper.IsListPropertyDefinitionWrapper
import maryk.core.properties.references.ReferenceType.LIST
import maryk.lib.exceptions.ParseException

/** Reference to a List property of type [T] and context [CX] */
open class ListReference<T: Any, CX: IsPropertyContext> internal constructor(
    propertyDefinition: IsListPropertyDefinitionWrapper<T, Any, ListDefinition<T, CX>, CX, *>,
    parentReference: CanHaveComplexChildReference<*, *, *, *>?
) : ValuePropertyReference<List<T>, List<Any>, IsListPropertyDefinitionWrapper<T, Any, ListDefinition<T, CX>, CX, *>, CanHaveComplexChildReference<*, *, *, *>>(
    propertyDefinition,
    parentReference
), HasEmbeddedPropertyReference<T> {
    override fun getEmbedded(name: String, context: IsPropertyContext?) = when(name[0]) {
        '@' -> ListItemReference(name.substring(1).toInt(), propertyDefinition.definition, this)
        else -> throw ParseException("Unknown List type $name[0]")
    }

    override fun getEmbeddedRef(reader: () -> Byte, context: IsPropertyContext?): IsPropertyReference<*, IsPropertyDefinition<*>, *> {
        return when(val index = initIntByVar(reader)) {
            0 -> ListItemReference(initIntByVar(reader), propertyDefinition.definition, this)
            else -> throw ParseException("Unknown List reference type $index")
        }
    }

    override fun getEmbeddedStorageRef(reader: () -> Byte, context: IsPropertyContext?, referenceType: CompleteReferenceType, isDoneReading: () -> Boolean): AnyPropertyReference {
        return if (referenceType == CompleteReferenceType.LIST) {
            ListItemReference(initUInt(reader).toInt(), propertyDefinition.definition, this)
        } else throw Exception("Unknown reference type below List: $referenceType")
    }

    override fun writeStorageBytes(writer: (byte: Byte) -> Unit) {
        this.parentReference?.writeStorageBytes(writer)
        this.propertyDefinition.index.writeVarIntWithExtraInfo(LIST.value, writer)
    }
}
