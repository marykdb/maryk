package maryk.core.properties.references

import maryk.core.exceptions.TypeException
import maryk.core.extensions.bytes.initUInt
import maryk.core.extensions.bytes.initUIntByVar
import maryk.core.extensions.bytes.writeVarIntWithExtraInfo
import maryk.core.properties.IsPropertyContext
import maryk.core.properties.definitions.IsPropertyDefinition
import maryk.core.properties.definitions.ListDefinition
import maryk.core.properties.definitions.wrapper.IsListPropertyDefinitionWrapper
import maryk.core.properties.references.CompleteReferenceType.LIST_ANY_VALUE
import maryk.core.properties.references.ReferenceType.LIST
import maryk.core.protobuf.ProtoBuf
import maryk.lib.exceptions.ParseException

/** Reference to a List property of type [T] and context [CX] */
open class ListReference<T : Any, CX : IsPropertyContext> internal constructor(
    propertyDefinition: IsListPropertyDefinitionWrapper<T, Any, ListDefinition<T, CX>, CX, *>,
    parentReference: CanHaveComplexChildReference<*, *, *, *>?
) : PropertyReferenceForValues<List<T>, List<Any>, IsListPropertyDefinitionWrapper<T, Any, ListDefinition<T, CX>, CX, *>, CanHaveComplexChildReference<*, *, *, *>>(
    propertyDefinition,
    parentReference
), HasEmbeddedPropertyReference<T> {
    override fun getEmbedded(name: String, context: IsPropertyContext?): AnyPropertyReference = when (name[0]) {
        '@' -> ListItemReference(name.substring(1).toUInt(), propertyDefinition.definition, this)
        '*' -> ListAnyItemReference(propertyDefinition.definition, this)
        else -> throw ParseException("Unknown List type $name[0]")
    }

    override fun getEmbeddedRef(
        reader: () -> Byte,
        context: IsPropertyContext?
    ): IsPropertyReference<*, IsPropertyDefinition<*>, *> {
        val protoKey = ProtoBuf.readKey(reader)
        return when (val index = protoKey.tag) {
            0u -> ListItemReference(initUIntByVar(reader), propertyDefinition.definition, this)
            1u -> ListAnyItemReference(propertyDefinition.definition, this)
            else -> throw ParseException("Unknown List reference type $index")
        }
    }

    override fun getEmbeddedStorageRef(
        reader: () -> Byte,
        context: IsPropertyContext?,
        referenceType: CompleteReferenceType,
        isDoneReading: () -> Boolean
    ): AnyPropertyReference {
        return when (referenceType) {
            CompleteReferenceType.LIST ->
                ListItemReference(initUInt(reader), propertyDefinition.definition, this)
            LIST_ANY_VALUE ->
                ListAnyItemReference(propertyDefinition.definition, this)
            else -> throw TypeException("Unknown reference type below List: $referenceType")
        }
    }

    override fun writeSelfStorageBytes(writer: (byte: Byte) -> Unit) {
        this.propertyDefinition.index.writeVarIntWithExtraInfo(LIST.value, writer)
    }
}
