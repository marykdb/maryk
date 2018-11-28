package maryk.core.properties.references

import maryk.core.extensions.bytes.writeVarIntWithExtraInfo
import maryk.core.properties.IsPropertyContext
import maryk.core.properties.definitions.IsFixedBytesEncodable
import maryk.core.properties.definitions.IsSimpleValueDefinition
import maryk.core.properties.definitions.wrapper.SetPropertyDefinitionWrapper
import maryk.core.properties.references.ReferenceType.SET
import maryk.core.protobuf.ProtoBuf
import maryk.lib.exceptions.ParseException

/**
 * Reference to a Set property of type [T] defined by [propertyDefinition] and context [CX]
 * under parent referred by [parentReference]
 */
open class SetReference<T: Any, CX: IsPropertyContext> internal constructor(
    propertyDefinition: SetPropertyDefinitionWrapper<T, CX, *>,
    parentReference: CanHaveComplexChildReference<*, *, *, *>?
) : ValuePropertyReference<Set<T>, Set<T>, SetPropertyDefinitionWrapper<T, CX, *>, CanHaveComplexChildReference<*, *, *, *>>(
    propertyDefinition,
    parentReference
), HasEmbeddedPropertyReference<T> {
    override fun getEmbedded(name: String, context: IsPropertyContext?) = when(name[0]) {
        '$' -> SetItemReference(
            propertyDefinition.definition.valueDefinition.fromString(
                name.substring(1)
            ),
            propertyDefinition.definition,
            this
        )
        else -> throw ParseException("Unknown Set type $name[0]")
    }

    override fun getEmbeddedRef(reader: () -> Byte, context: IsPropertyContext?): AnyPropertyReference {
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

    override fun getEmbeddedStorageRef(reader: () -> Byte, context: IsPropertyContext?, referenceType: CompleteReferenceType, isDoneReading: () -> Boolean): AnyPropertyReference {
        return if (referenceType == CompleteReferenceType.SET) {
            @Suppress("UNCHECKED_CAST")
            val setValueDefinition = (this.propertyDefinition.definition.valueDefinition as IsSimpleValueDefinition<T, *>)

            val setItem = setValueDefinition.readStorageBytes(
                (setValueDefinition as IsFixedBytesEncodable<*>).byteSize,
                reader
            )
            SetItemReference(setItem, propertyDefinition.definition, this)
        } else throw Exception("Unknown reference type below Set: $referenceType")
    }

    override fun writeStorageBytes(writer: (byte: Byte) -> Unit) {
        this.parentReference?.writeStorageBytes(writer)
        this.propertyDefinition.index.writeVarIntWithExtraInfo(SET.value, writer)
    }
}
