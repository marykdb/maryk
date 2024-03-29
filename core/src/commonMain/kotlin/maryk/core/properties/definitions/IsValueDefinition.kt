package maryk.core.properties.definitions

import maryk.core.properties.IsPropertyContext
import maryk.core.properties.definitions.wrapper.IsDefinitionWrapper
import maryk.core.protobuf.WireType
import maryk.core.protobuf.WriteCache
import maryk.core.protobuf.WriteCacheReader
import maryk.core.protobuf.WriteCacheWriter
import maryk.core.protobuf.calculateKeyAndContentLength
import maryk.core.protobuf.writeKeyWithLength
import maryk.lib.exceptions.ParseException

/**
 * Property Definition to define properties containing single values of [T].
 * This is used for simple single value properties and not for lists and maps.
 *
 * This type of definition is encodable within Collections because it can
 * read and write ProtoBuf without the key
 */
interface IsValueDefinition<T : Any, in CX : IsPropertyContext> : IsSubDefinition<T, CX> {
    val wireType: WireType

    override fun getEmbeddedByName(name: String): IsDefinitionWrapper<*, *, *, *>? = null
    override fun getEmbeddedByIndex(index: UInt): IsDefinitionWrapper<*, *, *, *>? = null

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

    override fun calculateTransportByteLengthWithKey(index: Int, value: T, cacher: WriteCacheWriter, context: CX?) =
        calculateKeyAndContentLength(this.wireType, index.toUInt(), cacher) {
            this.calculateTransportByteLength(value, cacher, context)
        }

    /**
     * Calculates the needed bytes to transport [value] with [context]
     * Caches any calculated lengths in [cacher]
     */
    fun calculateTransportByteLength(value: T, cacher: WriteCacheWriter, context: CX? = null): Int

    override fun writeTransportBytesWithKey(
        index: Int,
        value: T,
        cacheGetter: WriteCacheReader,
        writer: (byte: Byte) -> Unit,
        context: CX?
    ) {
        writeKeyWithLength(this.wireType, index.toUInt(), writer, cacheGetter)
        this.writeTransportBytes(value, cacheGetter, writer, context)
    }

    /** Writes value to bytes with [writer] and [context] for transportation */
    fun writeTransportBytes(value: T, cacheGetter: WriteCacheReader, writer: (byte: Byte) -> Unit, context: CX? = null)

    /** Writes [value] directly to transportable byte array */
    fun toTransportByteArray(value: T, context: CX? = null): ByteArray {
        val cache = WriteCache()
        val size = this.calculateTransportByteLength(value, cache, context)
        val output = ByteArray(size)
        var i = 0

        this.writeTransportBytes(value, cache, { output[i++] = it }, context)

        return output
    }
}
