package maryk.core.properties.references

import maryk.core.properties.IsPropertyContext
import maryk.core.properties.definitions.SetDefinition
import maryk.core.properties.exceptions.ParseException
import maryk.core.protobuf.ProtoBuf

/**
 * Reference to a set
 * @param <T> Type of reference
 * @param <CX> Context of reference
 */
open class SetReference<T: Any, in CX: IsPropertyContext> (
        propertyDefinition: SetDefinition<T, CX>,
        parentReference: CanHaveComplexChildReference<*, *, *>?
) : PropertyReference<Set<T>, SetDefinition<T, CX>, CanHaveComplexChildReference<*, *, *>>(
        propertyDefinition,
        parentReference
), HasEmbeddedPropertyReference<T> {
    override fun getEmbedded(name: String) = when(name[0]) {
        '$' -> SetItemReference(
                propertyDefinition.valueDefinition.fromString(
                        name.substring(1)
                ),
                this
        )
        else -> throw ParseException("Unknown Set type $name[0]")
    }

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