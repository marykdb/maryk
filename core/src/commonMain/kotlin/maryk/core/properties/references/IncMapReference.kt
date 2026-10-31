package maryk.core.properties.references

import maryk.core.exceptions.TypeException
import maryk.core.extensions.bytes.initIntByVar
import maryk.core.extensions.bytes.writeVarIntWithExtraInfo
import maryk.core.properties.IsPropertyContext
import maryk.core.properties.definitions.IsPropertyDefinition
import maryk.core.properties.definitions.wrapper.IncMapDefinitionWrapper
import maryk.core.properties.references.ReferenceType.MAP
import maryk.core.values.AbstractValues
import maryk.lib.exceptions.ParseException

/**
 * Reference to an incrementing map with key [K] and value [V] and context [CX]
 */
open class IncMapReference<K : Comparable<K>, V : Any, CX : IsPropertyContext> internal constructor(
    propertyDefinition: IncMapDefinitionWrapper<K, V, Any, CX, *>,
    parentReference: CanHaveComplexChildReference<*, *, *, *>?
) : PropertyReferenceForValues<Map<K, V>, Any, IncMapDefinitionWrapper<K, V, Any, CX, *>, CanHaveComplexChildReference<*, *, *, *>>(
        propertyDefinition,
        parentReference
    ),
    CanContainMapItemReference<Map<K, V>, IncMapDefinitionWrapper<K, V, Any, CX, *>, AbstractValues<*, *>>,
    HasEmbeddedPropertyReference<Map<K, V>>,
    IsMapReference<K, V, CX, IncMapDefinitionWrapper<K, V, Any, CX, *>> {
    override fun getEmbedded(name: String, context: IsPropertyContext?): AnyPropertyReference =
        super<IsMapReference>.getEmbedded(name, context)

    override fun getEmbeddedRef(
        reader: () -> Byte,
        context: IsPropertyContext?
    ): IsPropertyReference<*, IsPropertyDefinition<*>, *> = super<IsMapReference>.getEmbeddedRef(reader, context)

    override fun getEmbeddedStorageRef(
        reader: () -> Byte,
        context: IsPropertyContext?,
        referenceType: ReferenceType,
        isDoneReading: () -> Boolean
    ): AnyPropertyReference {
        return when (referenceType) {
            MAP -> {
                val mapKeyLength = initIntByVar {
                    if (isDoneReading()) {
                        throw ParseException("Missing map key length in storage reference")
                    }
                    reader()
                }
                if (mapKeyLength < 0) {
                    throw ParseException("Negative map key length in storage reference")
                }
                MapValueReference(
                    this.propertyDefinition.keyDefinition.readStorageBytes(mapKeyLength) {
                        if (isDoneReading()) {
                            throw ParseException("Map key length exceeds storage reference")
                        }
                        reader()
                    },
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
