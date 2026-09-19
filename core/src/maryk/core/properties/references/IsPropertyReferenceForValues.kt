package maryk.core.properties.references

import maryk.core.exceptions.UnexpectedValueException
import maryk.core.extensions.bytes.calculateVarByteLength
import maryk.core.extensions.bytes.calculateVarIntWithExtraInfoByteSize
import maryk.core.extensions.bytes.writeVarBytes
import maryk.core.extensions.bytes.writeVarIntWithExtraInfo
import maryk.core.properties.definitions.wrapper.IsDefinitionWrapper
import maryk.core.properties.graph.IsTransportablePropRefGraphNode
import maryk.core.properties.graph.PropRefGraphType.PropRef
import maryk.core.properties.references.ReferenceType.VALUE
import maryk.core.protobuf.WriteCacheReader
import maryk.core.protobuf.WriteCacheWriter
import maryk.core.values.AbstractValues

/**
 * Reference to a value property containing values of type [T] and conversion to [TO].
 * The property is defined by Property Definition Wrapper
 * [D] and referred by PropertyReference of type [P].
 */
interface IsPropertyReferenceForValues<
    T : Any,
    TO : Any,
    out D : IsDefinitionWrapper<T, TO, *, *>,
    out P : AnyPropertyReference
> : IsPropertyReference<T, D, AbstractValues<*, *>>, IsPropertyReferenceWithParent<T, D, P, AbstractValues<*, *>>, IsTransportablePropRefGraphNode {
    val name: String
    override val index get() = this.propertyDefinition.index
    override val graphType get() = PropRef

    /** Calculate the transport length of encoding this reference and cache length with [cacher] */
    override fun calculateTransportByteLength(cacher: WriteCacheWriter): Int {
        val parentLength = this.parentReference?.calculateTransportByteLength(cacher) ?: 0
        return this.propertyDefinition.index.calculateVarByteLength() + parentLength
    }

    /** Write transport bytes of property reference to [writer] anc get cache from [cacheGetter] */
    override fun writeTransportBytes(cacheGetter: WriteCacheReader, writer: (byte: Byte) -> Unit) {
        this.parentReference?.writeTransportBytes(cacheGetter, writer)
        this.propertyDefinition.index.writeVarBytes(writer)
    }

    override fun calculateSelfStorageByteLength() =
        this.propertyDefinition.index.calculateVarIntWithExtraInfoByteSize()

    override fun writeSelfStorageBytes(writer: (byte: Byte) -> Unit) {
        this.propertyDefinition.index.writeVarIntWithExtraInfo(VALUE.value, writer)
    }

    override fun resolve(values: AbstractValues<*, *>): T? {
        @Suppress("UNCHECKED_CAST")
        return values.original(propertyDefinition.index) as T?
    }

    override fun resolveFromAny(value: Any): Any {
        val valueAsValues = (value as? AbstractValues<*, *>)
            ?: throw UnexpectedValueException("Expected Values object for getting value by reference")

        return valueAsValues.original(this.propertyDefinition.index)
            ?: throw UnexpectedValueException("Not Found ${this.propertyDefinition.index}/${this.propertyDefinition.name} on Values")
    }
}

internal fun IsPropertyReferenceForValues<*, *, *, *>.generateCompleteName(): String =
    this.parentReference?.let {
        "${it.completeName}.$name"
    } ?: name
