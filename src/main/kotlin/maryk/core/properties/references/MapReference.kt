package maryk.core.properties.references

import maryk.core.properties.IsPropertyContext
import maryk.core.properties.definitions.IsPropertyDefinition
import maryk.core.properties.definitions.wrapper.DataObjectMapProperty
import maryk.core.properties.exceptions.ParseException
import maryk.core.protobuf.ProtoBuf

/**
 * Reference to a map
 * @param <T> Type of reference
 * @param <D> Definition of property
 */
open class MapReference<K: Any, V: Any, CX: IsPropertyContext> (
        propertyDefinition: DataObjectMapProperty<K, V, CX, *>,
        parentReference: CanHaveComplexChildReference<*, *, *>?
) : ValuePropertyReference<Map<K, V>, DataObjectMapProperty<K, V, CX, *>, CanHaveComplexChildReference<*, *, *>>(
        propertyDefinition,
        parentReference
), HasEmbeddedPropertyReference<Map<K, V>> {
    override fun getEmbedded(name: String): IsPropertyReference<*, *>  = when(name[0]) {
        '@' -> MapValueReference(
                propertyDefinition.keyDefinition.fromString(
                        name.substring(1)
                ),
                propertyDefinition.property,
                this
        )
        '$' -> MapKeyReference(
                propertyDefinition.keyDefinition.fromString(
                        name.substring(1)
                ),
                propertyDefinition.property,
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
                        this.propertyDefinition.property,
                        this
                )
            }
            1 -> {
                MapKeyReference(
                        this.propertyDefinition.keyDefinition.readTransportBytes(
                                ProtoBuf.getLength(protoKey.wireType, reader),
                                reader
                        ),
                        this.propertyDefinition.property,
                        this
                )
            }
            else -> throw ParseException("Unknown Key reference type ${protoKey.tag}")
        }
    }
}