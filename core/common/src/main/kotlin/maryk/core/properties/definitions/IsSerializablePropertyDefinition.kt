package maryk.core.properties.definitions

import maryk.core.json.IsJsonLikeReader
import maryk.core.json.IsJsonLikeWriter
import maryk.core.properties.IsPropertyContext
import maryk.core.properties.exceptions.ParseException
import maryk.core.protobuf.WriteCacheReader
import maryk.core.protobuf.WriteCacheWriter

/**
 * Interface to define this is a serializable property definition of [T]
 * with context [CX]
 */
interface IsSerializablePropertyDefinition<T: Any, in CX: IsPropertyContext> : IsPropertyDefinition<T> {
    /** Writes a value to Json with [writer] */
    fun writeJsonValue(value: T, writer: IsJsonLikeWriter, context: CX? = null)

    /**
     * Reads JSON values from [reader] with [context]
     * @throws ParseException when encountering unparsable content
     */
    fun readJson(reader: IsJsonLikeReader, context: CX? = null): T

    /**
     * Calculate length of bytes for [value] with key [index] to transport within optional [context]
     * Caches any calculated lengths to [cacher]
     */
    fun calculateTransportByteLengthWithKey(index: Int, value: T, cacher: WriteCacheWriter, context: CX? = null) : Int

    /**
     * Writes [value] and tag [index] and WireType with [writer] to bytes for transportation.
     * Get any cached sizes from [cacheGetter]
     * Optionally pass a [context] to write more complex properties which depend on other properties
     */
    fun writeTransportBytesWithKey(index: Int, value: T, cacheGetter: WriteCacheReader, writer: (byte: Byte) -> Unit, context: CX? = null)
}