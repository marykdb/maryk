package maryk.core.properties.definitions

import maryk.core.json.JsonReader
import maryk.core.json.JsonWriter
import maryk.core.properties.IsPropertyContext
import maryk.core.properties.exceptions.ParseException
import maryk.core.protobuf.ByteLengthContainer

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
    fun writeJsonValue(writer: JsonWriter, value: T)

    /** Reads JSON and returns values
     * @param context with possible context values for Dynamic Json readers
     * @param reader to read JSON from
     */
    @Throws(ParseException::class)
    fun readJson(context: CX?, reader: JsonReader): T


    /** Calculates the needed bytes to transport the value
     * @param value to get length of
     * @param lengthCacher to cache calculated lengths. Ordered so it can be read back in the same order
     * @return the total length
     */
    fun calculateTransportByteLengthWithKey(value: T, lengthCacher: (length: ByteLengthContainer) -> Unit) : Int

    /** Convert a value to bytes for transportation and adds the key with tag and wiretype
     * @param value to write
     * @param lengthCacheGetter to get next cached length
     * @param writer to write bytes to
     */
    fun writeTransportBytesWithKey(value: T, lengthCacheGetter: () -> Int, writer: (byte: Byte) -> Unit)
}