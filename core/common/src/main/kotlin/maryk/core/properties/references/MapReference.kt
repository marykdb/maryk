package maryk.core.properties.references

import maryk.core.properties.IsPropertyContext
import maryk.core.properties.definitions.IsPropertyDefinition
import maryk.core.properties.definitions.wrapper.MapPropertyDefinitionWrapper
import maryk.core.protobuf.ProtoBuf
import maryk.lib.exceptions.ParseException

/**
 * Reference to a map with key [K] and value [V] and context [CX]
 */
open class MapReference<K: Any, V: Any, CX: IsPropertyContext> internal constructor(
    propertyDefinition: MapPropertyDefinitionWrapper<K, V, Any, CX, *>,
    parentReference: CanHaveComplexChildReference<*, *, *>?
) : ValuePropertyReference<Map<K, V>, Any, MapPropertyDefinitionWrapper<K, V, Any, CX, *>, CanHaveComplexChildReference<*, *, *>>(
    propertyDefinition,
    parentReference
), HasEmbeddedPropertyReference<Map<K, V>> {
    override fun getEmbedded(name: String): AnyPropertyReference  = when(name[0]) {
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
        else -> throw ParseException("Unknown List type $name[0]")
    }

    override fun getEmbeddedRef(reader: () -> Byte): IsPropertyReference<*, IsPropertyDefinition<*>> {
        val protoKey = ProtoBuf.readKey(reader)
        return when(protoKey.tag) {
            0 -> {
                MapValueReference(
                    this.propertyDefinition.keyDefinition.readTransportBytes(
                        ProtoBuf.getLength(protoKey.wireType, reader),
                        reader
                    ),
                    this.propertyDefinition.definition,
                    this
                )
            }
            1 -> {
                MapKeyReference(
                    this.propertyDefinition.keyDefinition.readTransportBytes(
                        ProtoBuf.getLength(protoKey.wireType, reader),
                        reader
                    ),
                    this.propertyDefinition.definition,
                    this
                )
            }
            else -> throw ParseException("Unknown Key reference type ${protoKey.tag}")
        }
    }
}
