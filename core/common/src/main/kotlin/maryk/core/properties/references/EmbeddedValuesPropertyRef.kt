package maryk.core.properties.references

import maryk.core.exceptions.DefNotFoundException
import maryk.core.extensions.bytes.calculateVarByteLength
import maryk.core.extensions.bytes.initIntByVar
import maryk.core.extensions.bytes.writeVarBytes
import maryk.core.models.IsValuesDataModel
import maryk.core.objects.AbstractValues
import maryk.core.objects.Values
import maryk.core.properties.IsPropertyContext
import maryk.core.properties.PropertyDefinitions
import maryk.core.properties.definitions.contextual.ContextualEmbeddedValuesDefinition
import maryk.core.properties.definitions.wrapper.EmbeddedValuesPropertyDefinitionWrapper
import maryk.core.protobuf.WriteCacheReader
import maryk.core.protobuf.WriteCacheWriter
import maryk.core.query.ContainsDataModelContext

/**
 * Reference to a Embed property containing type Values, [P] PropertyDefinitions. Which is defined by
 * DataModel of type [DM] and expects context of type [CX].
 */
class EmbeddedValuesPropertyRef<
    DM : IsValuesDataModel<P>,
    P: PropertyDefinitions,
    CX: IsPropertyContext,
    PDM: IsValuesDataModel<P>
> internal constructor(
    propertyDefinition: EmbeddedValuesPropertyDefinitionWrapper<DM, P, CX, PDM>,
    parentReference: CanHaveComplexChildReference<*, *, *, *>?
): CanHaveComplexChildReference<Values<DM, P>, EmbeddedValuesPropertyDefinitionWrapper<DM, P, CX, PDM>, CanHaveComplexChildReference<*, *, *, *>, AbstractValues<*, *, *>>(
    propertyDefinition, parentReference
), HasEmbeddedPropertyReference<Values<DM, P>> {
    val name = this.propertyDefinition.name

    /** The name of property which is referenced */
    override val completeName: String get() = this.parentReference?.let {
        "${it.completeName}.$name"
    } ?: name

    override fun getEmbedded(name: String, context: IsPropertyContext?) =
        if (this.propertyDefinition.definition is ContextualEmbeddedValuesDefinition<*> && context is ContainsDataModelContext<*>) {
            (context.dataModel as? IsValuesDataModel<*>)?.properties?.get(name)?.getRef(this)
                    ?: throw DefNotFoundException("Embedded Definition with $name not found")
        } else {
            this.propertyDefinition.definition.dataModel.properties[name]?.getRef(this)
                    ?: throw DefNotFoundException("Embedded Definition with $name not found")
        }

    override fun getEmbeddedRef(reader: () -> Byte, context: IsPropertyContext?): AnyPropertyReference {
        val index = initIntByVar(reader)
        return this.propertyDefinition.definition.dataModel.properties[index]?.getRef(this)
                ?: throw DefNotFoundException("Embedded Definition with $name not found")
    }

    /** Calculate the transport length of encoding this reference and cache length with [cacher] */
    override fun calculateTransportByteLength(cacher: WriteCacheWriter): Int {
        val parentLength = this.parentReference?.calculateTransportByteLength(cacher) ?: 0
        return this.propertyDefinition.index.calculateVarByteLength() + parentLength
    }

    /** Write transport bytes of property reference to [writer] */
    override fun writeTransportBytes(cacheGetter: WriteCacheReader, writer: (byte: Byte) -> Unit) {
        this.parentReference?.writeTransportBytes(cacheGetter, writer)
        this.propertyDefinition.index.writeVarBytes(writer)
    }

    override fun resolve(values: AbstractValues<*, *, *>): Values<DM, P>? {
        @Suppress("UNCHECKED_CAST")
        return values.original(propertyDefinition.index) as Values<DM, P>?
    }
}
