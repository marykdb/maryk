package maryk.core.properties.definitions

import maryk.core.extensions.bytes.calculateVarByteLength
import maryk.core.extensions.bytes.writeVarBytes
import maryk.core.properties.IsPropertyContext
import maryk.core.properties.definitions.wrapper.IsPropertyDefinitionWrapper
import maryk.core.properties.exceptions.ParseException
import maryk.core.protobuf.ByteLengthContainer
import maryk.core.protobuf.ProtoBuf
import maryk.core.protobuf.WireType

/**
 * Abstract Property Definition to define properties.
 *
 * This is used for simple single value properties and not for lists and maps.
 * @param <T> Type of objects contained in property
 */
interface IsValueDefinition<T: Any, in CX: IsPropertyContext> : IsSubDefinition<T, CX> {
    val wireType: WireType

    override fun calculateTransportByteLengthWithKey(index: Int, value: T, lengthCacher: (length: ByteLengthContainer) -> Unit, context: CX?) : Int {
        var totalByteLength = 0
        totalByteLength += ProtoBuf.calculateKeyLength(index)

        if (this.wireType == WireType.LENGTH_DELIMITED) {
            // Take care length container is first cached before value is calculated
            // Otherwise byte lengths contained by value could be cached before
            // This way order is maintained
            val container = ByteLengthContainer()
            lengthCacher(container)

            // calculate field length
            this.calculateTransportByteLength(value, lengthCacher, context).let {
                container.length = it
                totalByteLength += it
                totalByteLength += it.calculateVarByteLength()
            }
        } else {
            // calculate field length
            totalByteLength += this.calculateTransportByteLength(value, lengthCacher, context)
        }

        return totalByteLength
    }

    /** Calculates the needed bytes to transport the value
     * @param value to get length of
     * @param lengthCacher to cache calculated lengths. Ordered so it can be read back in the same order
     * @param context with possible context values for Dynamic property writers
     * @return the total length
     */
    fun calculateTransportByteLength(value: T, lengthCacher: (length: ByteLengthContainer) -> Unit, context: CX? = null): Int

    override fun writeTransportBytesWithKey(index: Int, value: T, lengthCacheGetter: () -> Int, writer: (byte: Byte) -> Unit, context: CX?) {
        ProtoBuf.writeKey(index, this.wireType, writer)
        if (this.wireType == WireType.LENGTH_DELIMITED) {
            lengthCacheGetter().writeVarBytes(writer)
        }
        this.writeTransportBytes(value, lengthCacheGetter, writer, context)
    }

    /** Convert a value to bytes for transportation
     * @param value to write
     * @param writer to write bytes to
     * @param context with possible context values for Dynamic writers
     */
    fun writeTransportBytes(value: T, lengthCacheGetter: () -> Int, writer: (byte: Byte) -> Unit, context: CX? = null)

    override fun getEmbeddedByName(name: String): IsPropertyDefinitionWrapper<*, *, *>? = null

    override fun getEmbeddedByIndex(index: Int): IsPropertyDefinitionWrapper<*, *, *>? = null

    /** Get the value from a string
     * @param string to convert
     * @return the value
     * @param context with possible context values for Dynamic writers
     * @throws ParseException if conversion fails
     */
    fun fromString(string: String, context: CX? = null): T

    /** Convert value to String
     * @param value to convert
     * @param context with possible context values for Dynamic writers
     * @return value as String
     */
    fun asString(value: T, context: CX? = null) = value.toString()
}