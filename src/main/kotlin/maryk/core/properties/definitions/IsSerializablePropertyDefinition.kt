package maryk.core.properties.definitions

import maryk.core.json.JsonReader
import maryk.core.json.JsonWriter
import maryk.core.properties.IsPropertyContext
import maryk.core.properties.exceptions.ParseException
import maryk.core.protobuf.WriteCacheReader
import maryk.core.protobuf.WriteCacheWriter

/**
 * Interface to define this is a serializable property definition
 * @param <T> Type of Property contained in the definition
 * @param <CX> Context for dynamic property conversion
 */
interface IsSerializablePropertyDefinition<T: Any, in CX: IsPropertyContext> : IsPropertyDefinition<T> {
    /** Writes a value to Json
     * @param writer to write json to
     * @param value value to write
     */
    fun writeJsonValue(value: T, writer: JsonWriter, context: CX? = null)

    /** Reads JSON and returns values
     * @param context with possible context values for Dynamic Json readers
     * @param reader to read JSON from
     * @throws ParseException when encountering unparsable content
     */
    fun readJson(reader: JsonReader, context: CX? = null): T

    /** Calculates the needed bytes to transport the value
     * @param index to write this value for
     * @param value to get length of
     * @param cacher to cache calculated values. Ordered so it can be read back in the same order
     * @param context with possible context values for Dynamic property writers
     * @return the total length
     */
    fun calculateTransportByteLengthWithKey(index: Int, value: T, cacher: WriteCacheWriter, context: CX? = null) : Int

    /** Convert a value to bytes for transportation and adds the key with tag and wiretype
     * @param index to write this value for
     * @param value to write
     * @param cacheGetter to fetch next cached item
     * @param writer to write bytes to
     * @param context (optional) with context parameters for conversion (for dynamically dependent properties)
     */
    fun writeTransportBytesWithKey(index: Int, value: T, cacheGetter: WriteCacheReader, writer: (byte: Byte) -> Unit, context: CX? = null)
}