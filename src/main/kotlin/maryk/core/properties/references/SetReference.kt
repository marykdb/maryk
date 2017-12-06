package maryk.core.properties.references

import maryk.core.properties.IsPropertyContext
import maryk.core.properties.definitions.wrapper.DataObjectSetProperty
import maryk.core.properties.exceptions.ParseException
import maryk.core.protobuf.ProtoBuf

/**
 * Reference to a set
 * @param <T> Type of reference
 * @param <CX> Context of reference
 */
open class SetReference<T: Any, CX: IsPropertyContext> (
        propertyDefinition: DataObjectSetProperty<T, CX, *>,
        parentReference: CanHaveComplexChildReference<*, *, *>?
) : ValuePropertyReference<Set<T>, DataObjectSetProperty<T, CX, *>, CanHaveComplexChildReference<*, *, *>>(
        propertyDefinition,
        parentReference
), HasEmbeddedPropertyReference<T> {
    override fun getEmbedded(name: String) = when(name[0]) {
        '$' -> SetItemReference(
                propertyDefinition.property.valueDefinition.fromString(
                        name.substring(1)
                ),
                propertyDefinition.property,
                this
        )
        else -> throw ParseException("Unknown Set type $name[0]")
    }

    override fun getEmbeddedRef(reader: () -> Byte): IsPropertyReference<*, *> {
        val protoKey = ProtoBuf.readKey(reader)
        return when(protoKey.tag) {
            0 -> {
                SetItemReference(
                        this.propertyDefinition.property.valueDefinition.readTransportBytes(
                                ProtoBuf.getLength(protoKey.wireType, reader),
                                reader
                        ),
                        propertyDefinition.property,
                        this
                )
            }
            else -> throw ParseException("Unknown Set reference type $protoKey")
        }
    }
}