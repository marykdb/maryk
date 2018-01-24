package maryk.core.properties.definitions

import maryk.core.extensions.bytes.calculateVarByteLength
import maryk.core.extensions.bytes.writeVarBytes
import maryk.core.properties.IsPropertyContext
import maryk.core.properties.definitions.wrapper.IsPropertyDefinitionWrapper
import maryk.core.properties.exceptions.ParseException
import maryk.core.protobuf.ByteLengthContainer
import maryk.core.protobuf.ProtoBuf
import maryk.core.protobuf.WireType
import maryk.core.protobuf.WriteCacheReader
import maryk.core.protobuf.WriteCacheWriter

/**
 * Property Definition to define properties containing single values of [T].
 * This is used for simple single value properties and not for lists and maps.
 */
interface IsValueDefinition<T: Any, in CX: IsPropertyContext> : IsSubDefinition<T, CX> {
    val wireType: WireType

    override fun calculateTransportByteLengthWithKey(index: Int, value: T, cacher: WriteCacheWriter, context: CX?) : Int {
        var totalByteLength = 0
        totalByteLength += ProtoBuf.calculateKeyLength(index)

        if (this.wireType == WireType.LENGTH_DELIMITED) {
            // Take care length container is first cached before value is calculated
            // Otherwise byte lengths contained by value could be cached before
            // This way order is maintained
            val container = ByteLengthContainer()
            cacher.addLengthToCache(container)

            // calculate field length
            this.calculateTransportByteLength(value, cacher, context).let {
                container.length = it
                totalByteLength += it
                totalByteLength += it.calculateVarByteLength()
            }
        } else {
            // calculate field length
            totalByteLength += this.calculateTransportByteLength(value, cacher, context)
        }

        return totalByteLength
    }

    /**
     * Calculates the needed bytes to transport [value] with [context]
     * Caches any calculated lengths in [cacher]
     */
    fun calculateTransportByteLength(value: T, cacher: WriteCacheWriter, context: CX? = null): Int

    override fun writeTransportBytesWithKey(index: Int, value: T, cacheGetter: WriteCacheReader, writer: (byte: Byte) -> Unit, context: CX?) {
        ProtoBuf.writeKey(index, this.wireType, writer)
        if (this.wireType == WireType.LENGTH_DELIMITED) {
            cacheGetter.nextLengthFromCache().writeVarBytes(writer)
        }
        this.writeTransportBytes(value, cacheGetter, writer, context)
    }

    /** Writes value to bytes with [writer] and [context] for transportation */
    fun writeTransportBytes(value: T, cacheGetter: WriteCacheReader, writer: (byte: Byte) -> Unit, context: CX? = null)

    override fun getEmbeddedByName(name: String): IsPropertyDefinitionWrapper<*, *, *>? = null

    override fun getEmbeddedByIndex(index: Int): IsPropertyDefinitionWrapper<*, *, *>? = null

    /**
     * Get value from a [string]
     * Optionally pass a [context] to read more complex properties which depend on other properties
     * @throws ParseException if conversion fails
     */
    fun fromString(string: String, context: CX? = null): T

    /** Convert [value] to String
     * Optionally pass a [context] to read more complex properties which depend on other properties
     */
    fun asString(value: T, context: CX? = null) = value.toString()
}