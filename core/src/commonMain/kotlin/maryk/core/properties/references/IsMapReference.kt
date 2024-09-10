package maryk.core.properties.references

import maryk.core.exceptions.TypeException
import maryk.core.extensions.bytes.initIntByVar
import maryk.core.extensions.bytes.writeVarIntWithExtraInfo
import maryk.core.properties.IsPropertyContext
import maryk.core.properties.definitions.IsPropertyDefinition
import maryk.core.properties.definitions.wrapper.IsMapDefinitionWrapper
import maryk.core.properties.references.ReferenceType.MAP
import maryk.core.protobuf.ProtoBuf
import maryk.core.values.AbstractValues
import maryk.lib.exceptions.ParseException

/**
 * Reference to a map with key [K] and value [V] and context [CX]
 */
interface IsMapReference<K : Any, V : Any, CX : IsPropertyContext, D: IsMapDefinitionWrapper<K, V, Any, CX, *>> :
    IsPropertyReferenceForValues<Map<K, V>, Any, D, CanHaveComplexChildReference<*, *, *, *>>,
    CanContainMapItemReference<Map<K, V>, D, AbstractValues<*, *>>,
    HasEmbeddedPropertyReference<Map<K, V>> {
    override fun getEmbedded(name: String, context: IsPropertyContext?): AnyPropertyReference = when (name[0]) {
        '@' -> MapValueReference(
            propertyDefinition.definition.keyDefinition.fromString(
                name.substring(1)
            ),
            propertyDefinition.definition,
            this
        )
        '#' -> MapKeyReference(
            propertyDefinition.definition.keyDefinition.fromString(
                name.substring(1)
            ),
            propertyDefinition.definition,
            this
        )
        '*' -> MapAnyValueReference(
            propertyDefinition.definition,
            this
        )
        '^' -> IncMapAddIndexReference(
            name.substring(1).toInt(),
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
        return when (index) {
            0u -> MapValueReference(
                this.propertyDefinition.definition.keyDefinition.readTransportBytes(
                    ProtoBuf.getLength(protoKey.wireType, reader),
                    reader
                ),
                this.propertyDefinition.definition,
                this
            )
            1u -> MapKeyReference(
                this.propertyDefinition.definition.keyDefinition.readTransportBytes(
                    ProtoBuf.getLength(protoKey.wireType, reader),
                    reader
                ),
                this.propertyDefinition.definition,
                this
            )
            2u -> MapAnyValueReference(
                this.propertyDefinition.definition,
                this
            )
            3u -> IncMapAddIndexReference(
                initIntByVar(reader),
                this.propertyDefinition.definition,
                this
            )
            else -> throw ParseException("Unknown Key reference type ${protoKey.tag}")
        }
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
                    this.propertyDefinition.definition.keyDefinition.readStorageBytes(mapKeyLength, reader),
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
