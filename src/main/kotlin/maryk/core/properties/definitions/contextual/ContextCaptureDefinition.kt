package maryk.core.properties.definitions.contextual

import maryk.core.json.IsJsonLikeReader
import maryk.core.json.IsJsonLikeWriter
import maryk.core.json.JsonWriter
import maryk.core.properties.IsPropertyContext
import maryk.core.properties.definitions.IsSerializableFlexBytesEncodable
import maryk.core.properties.definitions.IsValueDefinition
import maryk.core.protobuf.WriteCacheReader
import maryk.core.protobuf.WriteCacheWriter

data class ContextCaptureDefinition<T: Any, in CX: IsPropertyContext>(
        val definition: IsValueDefinition<T, CX>,
        private val capturer: (CX?, T) -> Unit
) : IsValueDefinition<T, CX>, IsSerializableFlexBytesEncodable<T, CX> {
    override val wireType = definition.wireType
    override val indexed = definition.indexed
    override val searchable = definition.searchable
    override val required = definition.required
    override val final = definition.final

    override fun fromString(string: String, context: CX?)
            = this.definition.fromString(string, context).also { capturer(context, it) }

    override fun asString(value: T, context: CX?)
            = this.definition.asString(value.also {capturer(context, it)}, context)

    override fun calculateTransportByteLengthWithKey(index: Int, value: T, cacher: WriteCacheWriter, context: CX?)
            = this.definition.calculateTransportByteLengthWithKey(index, value.also { capturer(context, it) }, cacher, context)

    override fun calculateTransportByteLength(value: T, cacher: WriteCacheWriter, context: CX?)
            = this.definition.calculateTransportByteLength(value, cacher, context)

    override fun writeTransportBytes(value: T, cacheGetter: WriteCacheReader, writer: (byte: Byte) -> Unit, context: CX?)
            = this.definition.writeTransportBytes(value.also { capturer(context, it) }, cacheGetter, writer, context)

    override fun writeJsonValue(value: T, writer: IsJsonLikeWriter, context: CX?)
            = this.definition.writeJsonValue(value.also {capturer(context, it)}, writer, context)

    override fun readJson(reader: IsJsonLikeReader, context: CX?)
            = this.definition.readJson(reader, context).also {capturer(context, it)}

    override fun readTransportBytes(length: Int, reader: () -> Byte, context: CX?)
            = this.definition.readTransportBytes(length, reader, context).also {capturer(context, it)}
}