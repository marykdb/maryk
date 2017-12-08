package maryk.core.properties.definitions.contextual

import maryk.core.json.JsonReader
import maryk.core.json.JsonWriter
import maryk.core.objects.RootDataModel
import maryk.core.properties.IsPropertyContext
import maryk.core.properties.definitions.IsSerializableFlexBytesEncodable
import maryk.core.properties.definitions.IsValueDefinition
import maryk.core.properties.definitions.PropertyDefinitions
import maryk.core.protobuf.ByteLengthContainer
import maryk.core.protobuf.WireType

/** Definition for a reference to another DataObject*/
internal data class ContextualSubModelDefinition<in CX: IsPropertyContext>(
        val contextualResolver: (context: CX?) -> RootDataModel<Any, PropertyDefinitions<Any>>
): IsValueDefinition<Any, CX>, IsSerializableFlexBytesEncodable<Any, CX> {
    override val indexed = false
    override val searchable = false
    override val required = true
    override val final = true
    override val wireType = WireType.LENGTH_DELIMITED

    override fun fromString(string: String, context: CX?): Any {
        val stringIterator = string.iterator()
        return this.readJson(JsonReader { stringIterator.nextChar() }, context)
    }

    override fun asString(value: Any, context: CX?): String {
        var string = ""
        this.writeJsonValue(value, maryk.core.json.JsonWriter {
            string += it
        }, context)
        return string
    }

    override fun writeJsonValue(value: Any, writer: JsonWriter, context: CX?)
            = contextualResolver(context).writeJson(value, writer, context)

    override fun readJson(reader: JsonReader, context: CX?)
            = contextualResolver(context).readJsonToObject(reader, context)

    override fun calculateTransportByteLength(value: Any, lengthCacher: (length: ByteLengthContainer) -> Unit, context: CX?)
            = contextualResolver(context).calculateProtoBufLength(value, lengthCacher, context)

    override fun writeTransportBytes(value: Any, lengthCacheGetter: () -> Int, writer: (byte: Byte) -> Unit, context: CX?)
            = contextualResolver(context).writeProtoBuf(value, lengthCacheGetter, writer, context)

    override fun readTransportBytes(length: Int, reader: () -> Byte, context: CX?)
            = contextualResolver(context).readProtoBufToObject(length, reader, context)
}