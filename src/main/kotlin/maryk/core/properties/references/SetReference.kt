package maryk.core.properties.references

import maryk.core.properties.IsPropertyContext
import maryk.core.properties.definitions.wrapper.SetPropertyDefinitionWrapper
import maryk.core.properties.exceptions.ParseException
import maryk.core.protobuf.ProtoBuf

/**
 * Reference to a Set property of type [T] defined by [propertyDefinition] and context [CX]
 * under parent referred by [parentReference]
 */
open class SetReference<T: Any, CX: IsPropertyContext> (
    propertyDefinition: SetPropertyDefinitionWrapper<T, CX, *>,
    parentReference: CanHaveComplexChildReference<*, *, *>?
) : ValuePropertyReference<Set<T>, SetPropertyDefinitionWrapper<T, CX, *>, CanHaveComplexChildReference<*, *, *>>(
    propertyDefinition,
    parentReference
), HasEmbeddedPropertyReference<T> {
    override fun getEmbedded(name: String) = when(name[0]) {
        '$' -> SetItemReference(
            propertyDefinition.definition.valueDefinition.fromString(
                name.substring(1)
            ),
            propertyDefinition.definition,
            this
        )
        else -> throw ParseException("Unknown Set type $name[0]")
    }

    override fun getEmbeddedRef(reader: () -> Byte): IsPropertyReference<*, *> {
        val protoKey = ProtoBuf.readKey(reader)
        return when(protoKey.tag) {
            0 -> {
                SetItemReference(
                    this.propertyDefinition.definition.valueDefinition.readTransportBytes(
                        ProtoBuf.getLength(protoKey.wireType, reader),
                        reader
                    ),
                    propertyDefinition.definition,
                    this
                )
            }
            else -> throw ParseException("Unknown Set reference type $protoKey")
        }
    }
}