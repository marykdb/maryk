package maryk.core.properties.definitions.contextual

import maryk.core.models.AbstractValuesDataModel
import maryk.core.models.IsValuesDataModel
import maryk.core.properties.IsPropertyContext
import maryk.core.properties.PropertyDefinitions
import maryk.core.properties.definitions.IsEmbeddedValuesDefinition
import maryk.core.properties.definitions.PropertyDefinitionType
import maryk.core.protobuf.WireType
import maryk.core.protobuf.WriteCacheReader
import maryk.core.protobuf.WriteCacheWriter
import maryk.core.values.Values
import maryk.core.values.ValuesImpl
import maryk.json.IsJsonLikeReader
import maryk.json.IsJsonLikeWriter
import maryk.json.JsonReader
import maryk.json.JsonWriter

/** Definition for an embedded Values from a context resolved from [contextualResolver] */
internal data class ContextualEmbeddedValuesDefinition<CX : IsPropertyContext>(
    val contextualResolver: (context: CX?) -> AbstractValuesDataModel<IsValuesDataModel<PropertyDefinitions>, PropertyDefinitions, CX>
) : IsEmbeddedValuesDefinition<IsValuesDataModel<PropertyDefinitions>, PropertyDefinitions, CX> {
    override val dataModel: IsValuesDataModel<PropertyDefinitions>
        get() = throw Exception("dataModel is contextually determined")
    override val propertyDefinitionType = PropertyDefinitionType.Embed
    override val default: Values<IsValuesDataModel<PropertyDefinitions>, PropertyDefinitions>? = null
    override val required = true
    override val final = true
    override val wireType = WireType.LENGTH_DELIMITED

    override fun fromString(string: String, context: CX?): ValuesImpl {
        val stringIterator = string.iterator()
        return this.readJson(JsonReader { stringIterator.nextChar() }, context)
    }

    override fun asString(value: ValuesImpl, context: CX?): String {
        var string = ""
        this.writeJsonValue(value, JsonWriter {
            string += it
        }, context)
        return string
    }

    override fun writeJsonValue(value: ValuesImpl, writer: IsJsonLikeWriter, context: CX?) =
        contextualResolver(context).writeJson(value, writer, context)

    override fun readJson(reader: IsJsonLikeReader, context: CX?) =
        contextualResolver(context).readJson(reader, context)

    override fun calculateTransportByteLength(value: ValuesImpl, cacher: WriteCacheWriter, context: CX?) =
        contextualResolver(context).calculateProtoBufLength(value, cacher, null)

    override fun writeTransportBytes(
        value: ValuesImpl,
        cacheGetter: WriteCacheReader,
        writer: (byte: Byte) -> Unit,
        context: CX?
    ) =
        contextualResolver(context).writeProtoBuf(value, cacheGetter, writer, context)

    override fun readTransportBytes(length: Int, reader: () -> Byte, context: CX?) =
        contextualResolver(context).readProtoBuf(length, reader, context)
}
