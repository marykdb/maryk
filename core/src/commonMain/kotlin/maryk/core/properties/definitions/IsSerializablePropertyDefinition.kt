package maryk.core.properties.definitions

import maryk.core.exceptions.DefNotFoundException
import maryk.core.properties.IsPropertyContext
import maryk.core.protobuf.WriteCacheReader
import maryk.core.protobuf.WriteCacheWriter
import maryk.json.IsJsonLikeReader
import maryk.json.IsJsonLikeWriter
import maryk.lib.exceptions.ParseException

/**
 * Interface to define this is a property definition for which the value of [T] are serializable
 * with context [CX]
 */
interface IsSerializablePropertyDefinition<T : Any, in CX : IsPropertyContext> : IsPropertyDefinition<T> {
    /** Writes a value to Json with [writer] */
    fun writeJsonValue(value: T, writer: IsJsonLikeWriter, context: CX? = null)

    /**
     * Reads JSON values from [reader] with [context]
     * @throws ParseException when encountering unparsable content
     */
    fun readJson(reader: IsJsonLikeReader, context: CX? = null): T

    /**
     * Calculate length of bytes for [value] with key [index] to transport
     * Caches any calculated lengths to [cacher]
     */
    fun calculateTransportByteLengthWithKey(index: UInt, value: T, cacher: WriteCacheWriter) =
        this.calculateTransportByteLengthWithKey(index, value, cacher, null)

    /**
     * Calculate length of bytes for [value] with key [index] to transport within [context]
     * Caches any calculated lengths to [cacher]
     */
    fun calculateTransportByteLengthWithKey(index: UInt, value: T, cacher: WriteCacheWriter, context: CX?): Int

    /**
     * Writes [value] and tag [index] and WireType with [writer] to bytes for transportation.
     * Get any cached sizes from [cacheGetter]
     */
    fun writeTransportBytesWithKey(
        index: UInt,
        value: T,
        cacheGetter: WriteCacheReader,
        writer: (byte: Byte) -> Unit
    ) = this.writeTransportBytesWithKey(index, value, cacheGetter, writer, null)

    /**
     * Writes [value] and tag [index] and WireType with [writer] to bytes for transportation.
     * Get any cached sizes from [cacheGetter]
     * Pass a [context] to write more complex properties which depend on other properties
     */
    fun writeTransportBytesWithKey(
        index: UInt,
        value: T,
        cacheGetter: WriteCacheReader,
        writer: (byte: Byte) -> Unit,
        context: CX?
    )

    /**
     * Read value from [reader] with [context] until [length] is reached
     * [earlierValue] is the previous value read in same transport so additional values can be added. Primarily used
     * for lists/sets/maps
     * @throws DefNotFoundException if definition is not found to translate bytes
     */
    fun readTransportBytes(length: Int, reader: () -> Byte, context: CX? = null, earlierValue: T? = null): T
}
