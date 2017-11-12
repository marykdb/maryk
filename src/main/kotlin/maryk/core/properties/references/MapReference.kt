package maryk.core.properties.references

import maryk.core.properties.definitions.IsPropertyDefinition
import maryk.core.properties.definitions.MapDefinition
import maryk.core.properties.exceptions.ParseException
import maryk.core.protobuf.ProtoBuf

/**
 * Reference to a map
 * @param <T> Type of reference
 * @param <D> Definition of property
 */
open class MapReference<K: Any, V: Any> (
        propertyDefinition: MapDefinition<K, V, *>,
        parentReference: CanHaveComplexChildReference<*, *, *>?
) : PropertyReference<Map<K, V>, MapDefinition<K, V, *>, CanHaveComplexChildReference<*, *, *>>(
        propertyDefinition,
        parentReference
), HasEmbeddedPropertyReference<Map<K, V>> {
    override fun getEmbeddedRef(reader: () -> Byte): IsPropertyReference<*, IsPropertyDefinition<*>> {
        val protoKey = ProtoBuf.readKey(reader)
        return when(protoKey.tag) {
            0 -> {
                MapValueReference(
                        this.propertyDefinition.keyDefinition.readTransportBytes(
                                ProtoBuf.getLength(protoKey.wireType, reader),
                                reader
                        ),
                        this
                )
            }
            1 -> {
                MapKeyReference(
                        this.propertyDefinition.keyDefinition.readTransportBytes(
                                ProtoBuf.getLength(protoKey.wireType, reader),
                                reader
                        ),
                        this
                )
            }
            else -> throw ParseException("Unknown Key reference type ${protoKey.tag}")
        }
    }
}