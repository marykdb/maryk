package maryk.core.properties.definitions.contextual

import maryk.core.json.JsonReader
import maryk.core.json.JsonWriter
import maryk.core.properties.IsPropertyContext
import maryk.core.properties.definitions.IsSerializableFlexBytesEncodable
import maryk.core.properties.definitions.IsValueDefinition
import maryk.core.protobuf.ByteLengthContainer

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

    override fun calculateTransportByteLengthWithKey(index: Int, value: T, lengthCacher: (length: ByteLengthContainer) -> Unit, context: CX?)
            = this.definition.calculateTransportByteLengthWithKey(index, value.also { capturer(context, it) }, lengthCacher, context)

    override fun calculateTransportByteLength(value: T, lengthCacher: (length: ByteLengthContainer) -> Unit, context: CX?)
            = this.definition.calculateTransportByteLength(value, lengthCacher, context)

    override fun writeTransportBytes(value: T, lengthCacheGetter: () -> Int, writer: (byte: Byte) -> Unit, context: CX?)
            = this.definition.writeTransportBytes(value.also { capturer(context, it) }, lengthCacheGetter, writer, context)

    override fun writeJsonValue(value: T, writer: JsonWriter, context: CX?)
            = this.definition.writeJsonValue(value.also {capturer(context, it)}, writer, context)

    override fun readJson(reader: JsonReader, context: CX?)
            = this.definition.readJson(reader, context).also {capturer(context, it)}

    override fun readTransportBytes(length: Int, reader: () -> Byte, context: CX?)
            = this.definition.readTransportBytes(length, reader, context).also {capturer(context, it)}
}