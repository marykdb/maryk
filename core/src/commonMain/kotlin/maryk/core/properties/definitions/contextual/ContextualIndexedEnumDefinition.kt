package maryk.core.properties.definitions.contextual

import maryk.core.extensions.bytes.calculateVarByteLength
import maryk.core.extensions.bytes.initUIntByVar
import maryk.core.extensions.bytes.writeVarBytes
import maryk.core.properties.IsPropertyContext
import maryk.core.properties.definitions.IsContextualEncodable
import maryk.core.properties.definitions.IsMultiTypeDefinition
import maryk.core.properties.definitions.IsValueDefinition
import maryk.core.properties.definitions.wrapper.IsDefinitionWrapper
import maryk.core.properties.enum.IndexedEnum
import maryk.core.properties.enum.TypeEnum
import maryk.core.protobuf.WireType.VAR_INT
import maryk.core.protobuf.WriteCacheReader
import maryk.core.protobuf.WriteCacheWriter
import maryk.core.query.RequestContext
import maryk.json.IsJsonLikeReader
import maryk.json.IsJsonLikeWriter
import maryk.json.JsonToken.Value
import maryk.lib.exceptions.ParseException

/** Definition which refers to indexed enum definition based on context from [contextualResolver] */
data class ContextualIndexedEnumDefinition<CX : IsPropertyContext, CXI : IsPropertyContext, T : IndexedEnum, D : IsMultiTypeDefinition<TypeEnum<Any>, Any, RequestContext>>(
    val contextualResolver: (context: CX?) -> D,
    val contextTransformer: (context: CX?) -> CXI? = {
        @Suppress("UNCHECKED_CAST")
        it as CXI?
    },
    override val required: Boolean = true
) : IsValueDefinition<T, CX>, IsContextualEncodable<T, CX> {
    override val wireType = VAR_INT

    override val final = true

    override fun getEmbeddedByName(name: String): IsDefinitionWrapper<*, *, *, *>? = null
    override fun getEmbeddedByIndex(index: UInt): IsDefinitionWrapper<*, *, *, *>? = null

    @Suppress("UNCHECKED_CAST")
    override fun fromString(string: String, context: CX?) =
        contextualResolver(context).typeEnum.resolve(string) as T?
            ?: throw ParseException("Unknown Type enum $string")

    override fun writeJsonValue(value: T, writer: IsJsonLikeWriter, context: CX?) {
        writer.writeString(value.name)
    }

    override fun readJson(reader: IsJsonLikeReader, context: CX?) =
        reader.currentToken.let {
            if (it !is Value<*> || it.value !is String) {
                throw ParseException("Expected a String value and not $it")
            }
            fromString(it.value as String, context)
        }

    override fun calculateTransportByteLength(value: T, cacher: WriteCacheWriter, context: CX?) =
        value.index.calculateVarByteLength()

    override fun writeTransportBytes(
        value: T,
        cacheGetter: WriteCacheReader,
        writer: (byte: Byte) -> Unit,
        context: CX?
    ) {
        value.index.writeVarBytes(writer)
    }

    @Suppress("UNCHECKED_CAST")
    override fun readTransportBytes(length: Int, reader: () -> Byte, context: CX?, earlierValue: T?): T {
        val index = initUIntByVar(reader)
        return contextualResolver(context).typeEnum.resolve(index) as T?
            ?: throw ParseException("Unknown Type enum index $index")
    }
}
