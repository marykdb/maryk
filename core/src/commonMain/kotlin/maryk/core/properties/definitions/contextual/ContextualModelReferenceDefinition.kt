package maryk.core.properties.definitions.contextual

import maryk.core.exceptions.DefNotFoundException
import maryk.core.models.IsNamedDataModel
import maryk.core.properties.IsPropertyContext
import maryk.core.properties.definitions.IsContextualEncodable
import maryk.core.properties.definitions.IsValueDefinition
import maryk.core.protobuf.WireType
import maryk.core.protobuf.WriteCacheReader
import maryk.core.protobuf.WriteCacheWriter
import maryk.json.IsJsonLikeReader
import maryk.json.IsJsonLikeWriter
import maryk.json.JsonToken
import maryk.lib.bytes.calculateUTF8ByteLength
import maryk.lib.bytes.initString
import maryk.lib.bytes.writeUTF8Bytes
import maryk.lib.exceptions.ParseException

/** Definition for a reference to another DataObject resolved from context by [contextualResolver]. */
@Suppress("FunctionName")
fun <DM: IsNamedDataModel<*>, CX: IsPropertyContext> ContextualModelReferenceDefinition(
    contextualResolver: (context: CX?, name: String) -> Unit.() -> DM
) = ContextualModelReferenceDefinition<DM, CX, CX>(contextualResolver) {
    it
}

/**
 * Definition for a reference to another DataObject resolved from context by [contextualResolver].
 * Has a [contextTransformer] to transform context.
 */
data class ContextualModelReferenceDefinition<DM: IsNamedDataModel<*>, in CX: IsPropertyContext, CXI: IsPropertyContext>(
    val contextualResolver: (context: CXI?, name: String) -> Unit.() -> DM,
    val contextTransformer: (CX?) -> CXI?
): IsValueDefinition<IsDataModelReference<DM>, CX>, IsContextualEncodable<IsDataModelReference<DM>, CX> {
    override val required = true
    override val final = true
    override val wireType = WireType.LENGTH_DELIMITED

    override fun asString(value: IsDataModelReference<DM>, context: CX?) =
        value.name

    override fun fromString(string: String, context: CX?) =
        resolveContext(contextTransformer(context), string)

    override fun writeJsonValue(value: IsDataModelReference<DM>, writer: IsJsonLikeWriter, context: CX?) =
        writer.writeString(this.asString(value, context))

    override fun readJson(reader: IsJsonLikeReader, context: CX?) = reader.currentToken.let {
        when(it) {
            is JsonToken.Value<*> -> {
                val jsonValue = it.value
                when (jsonValue) {
                    null -> throw ParseException("Model reference cannot be null in JSON")
                    is String -> { this.fromString(jsonValue, context) }
                    else -> throw ParseException("Model reference has to be a String")
                }
            }
            else -> throw ParseException("Model reference has to be a value")
        }
    }

    override fun calculateTransportByteLength(value: IsDataModelReference<DM>, cacher: WriteCacheWriter, context: CX?) =
        value.name.calculateUTF8ByteLength()

    override fun writeTransportBytes(value: IsDataModelReference<DM>, cacheGetter: WriteCacheReader, writer: (byte: Byte) -> Unit, context: CX?) =
        value.name.writeUTF8Bytes(writer)

    override fun readTransportBytes(length: Int, reader: () -> Byte, context: CX?) =
        resolveContext(contextTransformer(context), initString(length, reader))

    private fun resolveContext(context: CXI?, name: String): IsDataModelReference<DM> {
        try {
            this.contextualResolver(context, name).let {
                return DataModelReference(name, it)
            }
        } catch (e: DefNotFoundException) {
            return LazyDataModelReference(name) {
                this.contextualResolver(context, name)
            }
        }
    }
}
