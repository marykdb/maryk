package maryk.core.properties.references

import maryk.core.properties.definitions.SetDefinition
import maryk.core.properties.exceptions.ParseException
import maryk.core.protobuf.ProtoBuf

/**
 * Reference to a set
 * @param <T> Type of reference
 * @param <D> Definition of property
 */
open class SetReference<T: Any> (
        propertyDefinition: SetDefinition<T, *>,
        parentReference: CanHaveComplexChildReference<*, *, *>?
) : PropertyReference<Set<T>, SetDefinition<T, *>, CanHaveComplexChildReference<*, *, *>>(
        propertyDefinition,
        parentReference
), HasEmbeddedPropertyReference<T> {
    override fun getEmbeddedRef(reader: () -> Byte): IsPropertyReference<*, *> {
        val protoKey = ProtoBuf.readKey(reader)
        return when(protoKey.tag) {
            0 -> {
                SetItemReference(
                        this.propertyDefinition.valueDefinition.readTransportBytes(
                                ProtoBuf.getLength(protoKey.wireType, reader),
                                reader
                        ),
                        this
                )
            }
            else -> throw ParseException("Unknown Set reference type $protoKey")
        }
    }
}