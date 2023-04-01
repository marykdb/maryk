package maryk.core.properties.definitions.contextual

import maryk.core.models.SimpleObjectDataModel
import maryk.core.models.serializers.IsDataModelSerializer
import maryk.core.models.serializers.ObjectDataModelSerializer
import maryk.core.properties.IsPropertyContext
import maryk.core.properties.ObjectPropertyDefinitions
import maryk.core.properties.definitions.IsContextualEncodable
import maryk.core.properties.definitions.IsValueDefinition
import maryk.core.protobuf.WireType.LENGTH_DELIMITED
import maryk.core.protobuf.WriteCacheReader
import maryk.core.protobuf.WriteCacheWriter
import maryk.core.values.ObjectValues
import maryk.json.IsJsonLikeReader
import maryk.json.IsJsonLikeWriter
import maryk.json.JsonReader
import maryk.json.JsonWriter

/** Definition for an embedded DataObject from a context resolved from [contextualResolver] */
data class ContextualEmbeddedObjectDefinition<CX : IsPropertyContext>(
    val contextualResolver: Unit.(context: CX?) -> SimpleObjectDataModel<Any, ObjectPropertyDefinitions<Any>>
) : IsValueDefinition<Any, CX>, IsContextualEncodable<Any, CX> {
    override val required = true
    override val final = true
    override val wireType = LENGTH_DELIMITED

    override fun fromString(string: String, context: CX?): Any {
        val stringIterator = string.iterator()
        return this.readJson(JsonReader { stringIterator.nextChar() }, context)
    }

    override fun asString(value: Any, context: CX?): String {
        var string = ""
        this.writeJsonValue(value, JsonWriter {
            string += it
        }, context)
        return string
    }

    override fun writeJsonValue(value: Any, writer: IsJsonLikeWriter, context: CX?) =
        contextualResolver(Unit, context).writeJson(value, writer, context)

    override fun readJson(reader: IsJsonLikeReader, context: CX?) =
        contextualResolver(Unit, context).readJson(reader, context).toDataObject()

    @Suppress("UNCHECKED_CAST")
    override fun calculateTransportByteLength(value: Any, cacher: WriteCacheWriter, context: CX?) =
        (contextualResolver(Unit, context).properties.Serializer as ObjectDataModelSerializer<Any, *, CX, CX>).calculateObjectProtoBufLength(value, cacher, context)

    @Suppress("UNCHECKED_CAST")
    override fun writeTransportBytes(
        value: Any,
        cacheGetter: WriteCacheReader,
        writer: (byte: Byte) -> Unit,
        context: CX?
    ) =
        (contextualResolver(Unit, context).properties.Serializer as ObjectDataModelSerializer<Any, *, CX, CX>).writeObjectProtoBuf(value, cacheGetter, writer, context)

    @Suppress("UNCHECKED_CAST")
    override fun readTransportBytes(
        length: Int,
        reader: () -> Byte,
        context: CX?,
        earlierValue: Any?
    ) =
        (contextualResolver(Unit, context).properties.Serializer as IsDataModelSerializer<ObjectValues<Any, *>, *, CX>).readProtoBuf(length, reader, context).toDataObject()
}
