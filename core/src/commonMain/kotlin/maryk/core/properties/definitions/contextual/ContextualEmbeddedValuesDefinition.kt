package maryk.core.properties.definitions.contextual

import maryk.core.exceptions.DefNotFoundException
import maryk.core.models.IsObjectDataModel
import maryk.core.properties.IsPropertyContext
import maryk.core.models.IsValuesDataModel
import maryk.core.models.TypedValuesDataModel
import maryk.core.properties.definitions.IsEmbeddedValuesDefinition
import maryk.core.properties.definitions.wrapper.EmbeddedValuesDefinitionWrapper
import maryk.core.properties.definitions.wrapper.ObjectDefinitionWrapperDelegateLoader
import maryk.core.protobuf.WireType.LENGTH_DELIMITED
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
    val contextualResolver: (context: CX?) -> TypedValuesDataModel<IsValuesDataModel>
) : IsEmbeddedValuesDefinition<IsValuesDataModel, CX> {
    override val dataModel: IsValuesDataModel
        get() = throw DefNotFoundException("dataModel is contextually determined")
    override val default: Values<IsValuesDataModel>? = null
    override val required = true
    override val final = true
    override val wireType = LENGTH_DELIMITED

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
        contextualResolver(context).Serializer.writeJson(value, writer, context)

    override fun readJson(reader: IsJsonLikeReader, context: CX?) =
        contextualResolver(context).Serializer.readJson(reader, context)

    override fun calculateTransportByteLength(value: ValuesImpl, cacher: WriteCacheWriter, context: CX?) =
        contextualResolver(context).Serializer.calculateProtoBufLength(value, cacher, null)

    override fun writeTransportBytes(
        value: ValuesImpl,
        cacheGetter: WriteCacheReader,
        writer: (byte: Byte) -> Unit,
        context: CX?
    ) =
        contextualResolver(context).Serializer.writeProtoBuf(value, cacheGetter, writer, context)

    override fun readTransportBytes(
        length: Int,
        reader: () -> Byte,
        context: CX?,
        earlierValue: Values<IsValuesDataModel>?
    ) =
        contextualResolver(context).Serializer.readProtoBuf(length, reader, context)
}

fun <DO: Any, CX: IsPropertyContext> IsObjectDataModel<DO>.embedContextual(
    index: UInt,
    getter: (DO) -> Values<out IsValuesDataModel>? = { null },
    contextualResolver: (context: CX?) -> TypedValuesDataModel<IsValuesDataModel>,
    name: String? = null,
    alternativeNames: Set<String>? = null,
    toSerializable: ((Values<IsValuesDataModel>?, IsPropertyContext?) -> Values<IsValuesDataModel>?)? = null,
    fromSerializable: ((Values<IsValuesDataModel>?) -> Values<IsValuesDataModel>?)? = null,
    shouldSerialize: ((Any) -> Boolean)? = null,
    capturer: ((IsPropertyContext, Values<IsValuesDataModel>) -> Unit)? = null
) = ObjectDefinitionWrapperDelegateLoader(this) { propName ->
    @Suppress("UNCHECKED_CAST")
    EmbeddedValuesDefinitionWrapper(
        index,
        name ?: propName,
        ContextualEmbeddedValuesDefinition(contextualResolver),
        alternativeNames,
        getter = getter as (Any) -> Values<IsValuesDataModel>?,
        capturer = capturer,
        toSerializable = toSerializable,
        fromSerializable = fromSerializable,
        shouldSerialize = shouldSerialize
    )
}
