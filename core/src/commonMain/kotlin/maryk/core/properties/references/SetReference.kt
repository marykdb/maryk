package maryk.core.properties.references

import maryk.core.exceptions.TypeException
import maryk.core.extensions.bytes.initIntByVar
import maryk.core.extensions.bytes.writeVarIntWithExtraInfo
import maryk.core.properties.IsPropertyContext
import maryk.core.properties.definitions.IsSimpleValueDefinition
import maryk.core.properties.definitions.wrapper.SetDefinitionWrapper
import maryk.core.properties.references.ReferenceType.SET
import maryk.core.protobuf.ProtoBuf
import maryk.core.values.AbstractValues
import maryk.lib.exceptions.ParseException

/**
 * Reference to a Set property of type [T] defined by [propertyDefinition] and context [CX]
 * under parent referred by [parentReference]
 */
open class SetReference<T : Any, CX : IsPropertyContext> internal constructor(
    propertyDefinition: SetDefinitionWrapper<T, CX, *>,
    parentReference: CanHaveComplexChildReference<*, *, *, *>?
) : PropertyReferenceForValues<Set<T>, Set<T>, SetDefinitionWrapper<T, CX, *>, CanHaveComplexChildReference<*, *, *, *>>(
        propertyDefinition,
        parentReference
    ),
    HasEmbeddedPropertyReference<T>,
    CanContainSetItemReference<Set<T>, SetDefinitionWrapper<T, CX, *>, AbstractValues<*, *, *>> {
    override fun getEmbedded(name: String, context: IsPropertyContext?) = when (name[0]) {
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
        return when (protoKey.tag) {
            0u -> {
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

    override fun getEmbeddedStorageRef(
        reader: () -> Byte,
        context: IsPropertyContext?,
        referenceType: ReferenceType,
        isDoneReading: () -> Boolean
    ): AnyPropertyReference {
        return when (referenceType) {
            SET -> {
                val setValueDefinition =
                    (this.propertyDefinition.definition.valueDefinition as IsSimpleValueDefinition<T, *>)

                val setItemLength = initIntByVar(reader)
                val setItem = setValueDefinition.readStorageBytes(
                    setItemLength,
                    reader
                )
                SetItemReference(setItem, propertyDefinition.definition, this)
            }
            else -> throw TypeException("Unknown reference type below Set: $referenceType")
        }
    }

    override fun writeSelfStorageBytes(writer: (byte: Byte) -> Unit) {
        this.propertyDefinition.index.writeVarIntWithExtraInfo(SET.value, writer)
    }
}
