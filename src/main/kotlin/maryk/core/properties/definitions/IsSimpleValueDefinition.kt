package maryk.core.properties.definitions

import maryk.core.exceptions.DefNotFoundException
import maryk.core.json.IsJsonLikeReader
import maryk.core.json.IsJsonLikeWriter
import maryk.core.json.JsonToken
import maryk.core.properties.IsPropertyContext
import maryk.core.properties.exceptions.ParseException
import maryk.core.protobuf.WriteCacheReader
import maryk.core.protobuf.WriteCacheWriter

/**
 * Abstract Property Definition to define properties of type [T].
 *
 * This is used for simple single value properties and not for lists and maps.
 */
interface IsSimpleValueDefinition<T: Any, in CX: IsPropertyContext> : IsValueDefinition<T, CX> {
    /**
     * Read stored bytes with [reader] until [length] and return value
     * @throws DefNotFoundException if definition is not found to translate bytes
     */
    fun readStorageBytes(length: Int, reader: () -> Byte): T

    /** Calculate byte length of a [value] */
    fun calculateStorageByteLength(value: T): Int

    /** Write a [value] to bytes with [writer] */
    fun writeStorageBytes(value: T, writer: (byte: Byte) -> Unit)

    override fun readTransportBytes(length: Int, reader: () -> Byte, context: CX?) = readStorageBytes(length, reader)

    override fun writeTransportBytes(value: T, cacheGetter: WriteCacheReader, writer: (byte: Byte) -> Unit, context: CX?) {
        writeStorageBytes(value, writer)
    }

    override fun calculateTransportByteLength(value: T, cacher: WriteCacheWriter, context: CX?) =
        this.calculateTransportByteLength(value)

    /** Calculates the needed bytes to transport the [value] */
    fun calculateTransportByteLength(value: T): Int

    /** Convert [value] to String */
    fun asString(value: T) = value.toString()

    override fun asString(value: T, context: CX?) = this.asString(value)

    /**
     * Get a value from [string]
     * @throws ParseException when encountering unparsable content
     */
    fun fromString(string: String): T

    override fun fromString(string: String, context: CX?) = this.fromString(string)

    override fun writeJsonValue(value: T, writer: IsJsonLikeWriter, context: CX?) {
        writer.writeString(
            this.asString(value, context)
        )
    }

    override fun readJson(reader: IsJsonLikeReader, context: CX?): T {
        if (reader.currentToken !is JsonToken.ObjectValue && reader.currentToken !is JsonToken.ArrayValue) {
            throw ParseException("JSON value should be a simple value")
        }
        return reader.lastValue?.let {
            this.fromString(it, context)
        } ?: throw ParseException("JSON value cannot be null")
    }
}