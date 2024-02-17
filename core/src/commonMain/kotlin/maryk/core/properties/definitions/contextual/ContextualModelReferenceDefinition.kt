package maryk.core.properties.definitions.contextual

import maryk.core.exceptions.DefNotFoundException
import maryk.core.models.IsDataModel
import maryk.core.properties.IsPropertyContext
import maryk.core.properties.definitions.IsContextualEncodable
import maryk.core.properties.definitions.IsValueDefinition
import maryk.core.protobuf.WireType.LENGTH_DELIMITED
import maryk.core.protobuf.WriteCacheReader
import maryk.core.protobuf.WriteCacheWriter
import maryk.json.IsJsonLikeReader
import maryk.json.IsJsonLikeWriter
import maryk.json.JsonToken.Value
import maryk.lib.bytes.calculateUTF8ByteLength
import maryk.lib.bytes.initString
import maryk.lib.bytes.writeUTF8Bytes
import maryk.lib.exceptions.ParseException

/** Definition for a reference to another DataObject resolved from context by [contextualResolver]. */
fun <DM : IsDataModel, CX : IsPropertyContext> ContextualModelReferenceDefinition(
    contextualResolver: Unit.(context: CX?, name: String) -> IsDataModelReference<DM>
) = ContextualModelReferenceDefinition<DM, CX, CX>(contextualResolver) {
    it
}

/**
 * Definition for a reference to another DataObject resolved from context by [contextualResolver].
 * Has a [contextTransformer] to transform context.
 */
data class ContextualModelReferenceDefinition<DM : IsDataModel, in CX : IsPropertyContext, CXI : IsPropertyContext>(
    val contextualResolver: Unit.(context: CXI?, name: String) -> IsDataModelReference<DM>,
    val contextTransformer: Unit.(CX?) -> CXI?
) : IsValueDefinition<IsDataModelReference<DM>, CX>, IsContextualEncodable<IsDataModelReference<DM>, CX> {
    override val required = true
    override val final = true
    override val wireType = LENGTH_DELIMITED

    override fun asString(value: IsDataModelReference<DM>, context: CX?) =
        value.name

    override fun fromString(string: String, context: CX?) =
        resolveContext(contextTransformer(Unit, context), string)

    override fun writeJsonValue(value: IsDataModelReference<DM>, writer: IsJsonLikeWriter, context: CX?) =
        writer.writeString(this.asString(value, context))

    override fun readJson(reader: IsJsonLikeReader, context: CX?) = reader.currentToken.let {
        when (it) {
            is Value<*> -> {
                when (val jsonValue = it.value) {
                    null -> throw ParseException("Model reference cannot be null in JSON")
                    is String -> this.fromString(jsonValue, context)
                    else -> throw ParseException("Model reference has to be a String")
                }
            }
            else -> throw ParseException("Model reference has to be a value")
        }
    }

    override fun calculateTransportByteLength(value: IsDataModelReference<DM>, cacher: WriteCacheWriter, context: CX?) =
        value.name.calculateUTF8ByteLength()

    override fun writeTransportBytes(
        value: IsDataModelReference<DM>,
        cacheGetter: WriteCacheReader,
        writer: (byte: Byte) -> Unit,
        context: CX?
    ) =
        value.name.writeUTF8Bytes(writer)

    override fun readTransportBytes(
        length: Int,
        reader: () -> Byte,
        context: CX?,
        earlierValue: IsDataModelReference<DM>?
    ) =
        resolveContext(contextTransformer(Unit, context), initString(length, reader))

    private fun resolveContext(context: CXI?, name: String): IsDataModelReference<DM> {
        try {
            return this.contextualResolver(Unit, context, name)
        } catch (e: DefNotFoundException) {
            return LazyDataModelReference(name) {
                this.contextualResolver(Unit, context, name).also {
                    if (it is LazyDataModelReference<*>) {
                        throw DefNotFoundException("Could not resolve DataModel $name, was it processed before or provided in dependents in the context?")
                    }
                }.get
            }
        }
    }
}
