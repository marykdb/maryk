package maryk.core.properties.definitions.contextual

import maryk.core.exceptions.DefNotFoundException
import maryk.core.models.SimpleValuesDataModel
import maryk.core.models.serializers.IsDataModelSerializer
import maryk.core.properties.IsPropertyContext
import maryk.core.properties.IsValuesPropertyDefinitions
import maryk.core.properties.ObjectPropertyDefinitions
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
    val contextualResolver: Unit.(context: CX?) -> SimpleValuesDataModel<IsValuesPropertyDefinitions>
) : IsEmbeddedValuesDefinition<IsValuesPropertyDefinitions, CX> {
    override val dataModel: IsValuesPropertyDefinitions
        get() = throw DefNotFoundException("dataModel is contextually determined")
    override val default: Values<IsValuesPropertyDefinitions>? = null
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
        contextualResolver(Unit, context).writeJson(value, writer, context)

    override fun readJson(reader: IsJsonLikeReader, context: CX?) =
        contextualResolver(Unit, context).readJson(reader, context)

    @Suppress("UNCHECKED_CAST")
    override fun calculateTransportByteLength(value: ValuesImpl, cacher: WriteCacheWriter, context: CX?) =
        (contextualResolver(Unit, context).properties.Serializer as IsDataModelSerializer<ValuesImpl, *, *>).calculateProtoBufLength(value, cacher, null)

    @Suppress("UNCHECKED_CAST")
    override fun writeTransportBytes(
        value: ValuesImpl,
        cacheGetter: WriteCacheReader,
        writer: (byte: Byte) -> Unit,
        context: CX?
    ) =
        (contextualResolver(Unit, context).properties.Serializer as IsDataModelSerializer<ValuesImpl, *, CX>).writeProtoBuf(value, cacheGetter, writer, context)

    @Suppress("UNCHECKED_CAST")
    override fun readTransportBytes(
        length: Int,
        reader: () -> Byte,
        context: CX?,
        earlierValue: Values<IsValuesPropertyDefinitions>?
    ) =
        (contextualResolver(Unit, context).properties.Serializer as IsDataModelSerializer<ValuesImpl, *, CX>).readProtoBuf(length, reader, context)
}

fun <DO: Any, CX: IsPropertyContext> ObjectPropertyDefinitions<DO>.embedContextual(
    index: UInt,
    getter: (DO) -> Values<out IsValuesPropertyDefinitions>? = { null },
    contextualResolver: Unit.(context: CX?) -> SimpleValuesDataModel<IsValuesPropertyDefinitions>,
    name: String? = null,
    alternativeNames: Set<String>? = null,
    toSerializable: (Unit.(Values<IsValuesPropertyDefinitions>?, IsPropertyContext?) -> Values<IsValuesPropertyDefinitions>?)? = null,
    fromSerializable: (Unit.(Values<IsValuesPropertyDefinitions>?) -> Values<IsValuesPropertyDefinitions>?)? = null,
    shouldSerialize: (Unit.(Any) -> Boolean)? = null,
    capturer: (Unit.(IsPropertyContext, Values<IsValuesPropertyDefinitions>) -> Unit)? = null
) = ObjectDefinitionWrapperDelegateLoader(this) { propName ->
    @Suppress("UNCHECKED_CAST")
    EmbeddedValuesDefinitionWrapper(
        index,
        name ?: propName,
        ContextualEmbeddedValuesDefinition(contextualResolver),
        alternativeNames,
        getter = getter as (Any) -> Values<IsValuesPropertyDefinitions>?,
        capturer = capturer,
        toSerializable = toSerializable,
        fromSerializable = fromSerializable,
        shouldSerialize = shouldSerialize
    )
}
