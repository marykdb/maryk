package maryk.core.properties.references

import maryk.core.exceptions.TypeException
import maryk.core.extensions.bytes.initIntByVar
import maryk.core.extensions.bytes.writeVarIntWithExtraInfo
import maryk.core.properties.IsPropertyContext
import maryk.core.properties.definitions.IsPropertyDefinition
import maryk.core.properties.definitions.wrapper.MapPropertyDefinitionWrapper
import maryk.core.properties.references.ReferenceType.MAP
import maryk.core.protobuf.ProtoBuf
import maryk.lib.exceptions.ParseException

/**
 * Reference to a map with key [K] and value [V] and context [CX]
 */
open class MapReference<K : Any, V : Any, CX : IsPropertyContext> internal constructor(
    propertyDefinition: MapPropertyDefinitionWrapper<K, V, Any, CX, *>,
    parentReference: CanHaveComplexChildReference<*, *, *, *>?
) : PropertyReferenceForValues<Map<K, V>, Any, MapPropertyDefinitionWrapper<K, V, Any, CX, *>, CanHaveComplexChildReference<*, *, *, *>>(
        propertyDefinition,
        parentReference
    ),
    HasEmbeddedPropertyReference<Map<K, V>> {
    override fun getEmbedded(name: String, context: IsPropertyContext?): AnyPropertyReference = when (name[0]) {
        '@' -> MapValueReference(
            propertyDefinition.keyDefinition.fromString(
                name.substring(1)
            ),
            propertyDefinition.definition,
            this
        )
        '$' -> MapKeyReference(
            propertyDefinition.keyDefinition.fromString(
                name.substring(1)
            ),
            propertyDefinition.definition,
            this
        )
        '*' -> MapAnyValueReference(
            propertyDefinition.definition,
            this
        )
        else -> throw ParseException("Unknown List type $name[0]")
    }

    override fun getEmbeddedRef(
        reader: () -> Byte,
        context: IsPropertyContext?
    ): IsPropertyReference<*, IsPropertyDefinition<*>, *> {
        val protoKey = ProtoBuf.readKey(reader)
        val index = protoKey.tag
        // Because of an issue in JS not working with unsigned it needs to be an if
        // https://youtrack.jetbrains.com/issue/KT-31145
        @Suppress("CascadeIf")
        return if (index == 0u) {
            MapValueReference(
                this.propertyDefinition.keyDefinition.readTransportBytes(
                    ProtoBuf.getLength(protoKey.wireType, reader),
                    reader
                ),
                this.propertyDefinition.definition,
                this
            )
        }
        else if (index == 1u) {
            MapKeyReference(
                this.propertyDefinition.keyDefinition.readTransportBytes(
                    ProtoBuf.getLength(protoKey.wireType, reader),
                    reader
                ),
                this.propertyDefinition.definition,
                this
            )
        }
        else if (index == 2u) {
            MapAnyValueReference(
                this.propertyDefinition.definition,
                this
            )
        }
        else throw ParseException("Unknown Key reference type ${protoKey.tag}")
    }

    override fun getEmbeddedStorageRef(
        reader: () -> Byte,
        context: IsPropertyContext?,
        referenceType: ReferenceType,
        isDoneReading: () -> Boolean
    ): AnyPropertyReference {
        return when (referenceType) {
            MAP -> {
                val mapKeyLength = initIntByVar(reader)
                MapValueReference(
                    this.propertyDefinition.keyDefinition.readStorageBytes(mapKeyLength, reader),
                    this.propertyDefinition.definition,
                    this
                )
            }
            else -> throw TypeException("Unknown map ref type $referenceType")
        }
    }

    override fun writeSelfStorageBytes(writer: (byte: Byte) -> Unit) {
        this.propertyDefinition.index.writeVarIntWithExtraInfo(MAP.value, writer)
    }
}
