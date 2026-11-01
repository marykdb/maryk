package maryk.core.query.filters

import maryk.core.models.IsObjectDataModel
import maryk.core.models.serializers.protoBufContinuations
import maryk.core.properties.definitions.EMPTY_COLLECTION_TAG_OFFSET
import maryk.core.properties.definitions.IsMultiTypeDefinition
import maryk.core.properties.definitions.wrapper.ListDefinitionWrapper
import maryk.core.properties.enum.IndexedEnum
import maryk.core.properties.references.CanHaveComplexChildReference
import maryk.core.properties.references.IsPropertyReference
import maryk.core.properties.types.TypedValue
import maryk.core.protobuf.ProtoBuf
import maryk.core.protobuf.WireType.LENGTH_DELIMITED
import maryk.core.protobuf.WriteCacheReader
import maryk.core.protobuf.WriteCacheWriter
import maryk.core.protobuf.addProtoByteLength
import maryk.core.protobuf.calculateKeyAndContentLength
import maryk.core.protobuf.writeKeyWithLength
import maryk.core.query.DefinitionsContext
import maryk.core.query.RequestContext
import maryk.core.values.AbstractValues
import maryk.core.values.MutableValueItems
import maryk.core.values.ObjectValues
import maryk.core.values.copyValueWithOwnedContent
import maryk.json.IsJsonLikeReader
import maryk.json.IsJsonLikeWriter
import maryk.json.JsonToken.EndArray
import maryk.json.JsonToken.NullValue
import maryk.json.JsonToken.StartArray
import maryk.json.JsonToken.StartDocument
import maryk.json.JsonToken.Stopped
import maryk.json.JsonToken.Value
import maryk.json.TokenWithType
import maryk.lib.exceptions.ParseException
import maryk.yaml.IsYamlReader
import maryk.yaml.UnknownYamlTag
import maryk.yaml.YamlWriter

private typealias FilterValue = TypedValue<FilterType, IsFilter>
private typealias FilterListProperty = ListDefinitionWrapper<FilterValue, IsFilter, RequestContext, IsFilterList>

/**
 * Composite filters have a bounded, shallow codec instead of re-entering the generic
 * list/multi-type/embedded-model serializers at every level. The latter multiply
 * the call stack enough to overflow Android's instrumentation stack before depth 64.
 * Leaf filters retain their ordinary definitions, contexts and write-cache entries.
 */
@Suppress("UNCHECKED_CAST")
private fun filterListProperty(type: FilterType): FilterListProperty = when (type) {
    FilterType.And -> And.filters
    FilterType.Or -> Or.filters
    FilterType.Not -> Not.filters
    else -> throw ParseException("$type is not a composite filter")
} as FilterListProperty

private fun filterListModel(type: FilterType): Any = when (type) {
    FilterType.And -> And
    FilterType.Or -> Or
    FilterType.Not -> Not
    else -> throw ParseException("$type is not a composite filter")
}

private fun FilterType.isComposite() = this == FilterType.And || this == FilterType.Or || this == FilterType.Not

private fun newFilterList(type: FilterType, filters: List<FilterValue>?): IsFilterList = when (type) {
    FilterType.And -> if (filters == null) And(And.create(setDefaults = false) {}) else And(filters.map { it.value })
    FilterType.Or -> if (filters == null) Or(Or.create(setDefaults = false) {}) else Or(filters.map { it.value })
    FilterType.Not -> if (filters == null) Not(Not.create(setDefaults = false) {}) else Not(filters.map { it.value })
    else -> throw ParseException("$type is not a composite filter")
}

@Suppress("UNCHECKED_CAST")
private fun FilterListProperty.filterDefinition() =
    definition.valueDefinition as IsMultiTypeDefinition<FilterType, IsFilter, RequestContext>

internal fun writeFilterListJson(value: IsFilterList, writer: IsJsonLikeWriter, context: RequestContext?) {
    withFilterNesting(context) { filterContext ->
        val definition = filterListProperty(value.filterType).filterDefinition()
        writer.writeStartArray()
        for (child in value.filters) {
            if (child is IsFilterList) {
                if (writer is YamlWriter) writer.writeTag("!${child.filterType.name}") else {
                    writer.writeStartArray()
                    writer.writeString(child.filterType.name)
                }
                writeFilterListJson(child, writer, filterContext)
                if (writer !is YamlWriter) writer.writeEndArray()
            } else definition.writeJsonValue(TypedValue(child.filterType, child), writer, filterContext)
        }
        writer.writeEndArray()
    }
}

internal fun readFilterListJson(type: FilterType, reader: IsJsonLikeReader, context: RequestContext?): List<FilterValue> =
    withFilterNesting(context) { filterContext ->
        if (reader.currentToken == StartDocument) reader.nextToken()
        if (reader.currentToken == NullValue) return@withFilterNesting emptyList()
        if (reader.currentToken !is StartArray) throw ParseException("JSON filter value should be an Array")
        val definition = filterListProperty(type).filterDefinition()
        val result = mutableListOf<FilterValue>()
        while (reader.nextToken() !== EndArray) {
            if (reader.currentToken is Stopped) throw ParseException("JSON filter input ended before the collection was complete")
            val childType = if (reader is IsYamlReader) {
                when (val tag = (reader.currentToken as? TokenWithType)?.type) {
                    is IndexedEnum -> FilterType.resolve(tag.name)?.takeIf { it.index == tag.index }
                        ?: throw ParseException("Unknown filter tag $tag")
                    is UnknownYamlTag -> FilterType.resolve(tag.name) ?: throw ParseException("Unknown filter type ${tag.name}")
                    else -> throw ParseException("Expected a YAML filter type tag")
                }
            } else {
                if (reader.currentToken !is StartArray) throw ParseException("Expected a multi type array")
                val token = reader.nextToken() as? Value<*>
                val name = token?.value as? String ?: throw ParseException("Expected a filter type name")
                val resolved = FilterType.resolve(name) ?: throw ParseException("Invalid multi type name $name")
                reader.nextToken()
                resolved
            }
            val child = if (childType.isComposite()) {
                newFilterList(childType, readFilterListJson(childType, reader, filterContext))
            } else {
                val leaf = definition.definition(childType)
                    ?: throw ParseException("Unknown filter type $childType")
                leaf.readJson(reader, filterContext)
            }
            if (reader !is IsYamlReader && reader.nextToken() !== EndArray) {
                throw ParseException("Multi type array must contain exactly a type name and a value")
            }
            result.add(TypedValue(childType, child))
        }
        result
    }

internal fun calculateFilterListProtoBufLength(value: IsFilterList, cacher: WriteCacheWriter, context: RequestContext?): Int =
    withFilterNesting(context) { filterContext ->
        if (value.filters.isEmpty()) return@withFilterNesting ProtoBuf.calculateKeyLength(1u + EMPTY_COLLECTION_TAG_OFFSET) + 1
        val property = filterListProperty(value.filterType)
        val list = property.definition
        val definition = property.filterDefinition()
        val model = filterListModel(value.filterType)
        @Suppress("UNCHECKED_CAST")
        filterContext.collectInjectLevel(model) { parent -> property.ref(parent) as IsPropertyReference<in Any, *, *> }
        var length = 0
        for ((position, child) in value.filters.withIndex()) {
            val typed = TypedValue(child.filterType, child)
            filterContext.collectInjectLevel(list, list.getItemPropertyRefCreator(position.toUInt(), typed))
            val childLength = if (child is IsFilterList) {
                filterContext.collectInjectLevel(definition) { parent ->
                    @Suppress("UNCHECKED_CAST")
                    (definition.typedValueRef(child.filterType, parent as CanHaveComplexChildReference<*, *, *, *>?) as IsPropertyReference<in Any, *, *>)
                }
                val size = calculateKeyAndContentLength(LENGTH_DELIMITED, 1u, cacher) {
                    calculateKeyAndContentLength(LENGTH_DELIMITED, child.filterType.index, cacher) {
                        calculateFilterListProtoBufLength(child, cacher, filterContext)
                    }
                }
                filterContext.closeInjectLevel(definition)
                size
            } else definition.calculateTransportByteLengthWithKey(1, typed, cacher, filterContext)
            length = length.addProtoByteLength(childLength)
        }
        filterContext.closeInjectLevel(list)
        filterContext.closeInjectLevel(model)
        length
    }

internal fun writeFilterListProtoBuf(
    value: IsFilterList,
    cacheGetter: WriteCacheReader,
    writer: (Byte) -> Unit,
    context: RequestContext?
) {
    withFilterNesting(context) { filterContext ->
        if (value.filters.isEmpty()) {
            ProtoBuf.writeKey(1u + EMPTY_COLLECTION_TAG_OFFSET, LENGTH_DELIMITED, writer)
            writer(0)
            return@withFilterNesting
        }
        val definition = filterListProperty(value.filterType).filterDefinition()
        for (child in value.filters) {
            if (child is IsFilterList) {
                writeKeyWithLength(LENGTH_DELIMITED, 1u, writer, cacheGetter)
                writeKeyWithLength(LENGTH_DELIMITED, child.filterType.index, writer, cacheGetter)
                writeFilterListProtoBuf(child, cacheGetter, writer, filterContext)
            } else definition.writeTransportBytesWithKey(1, TypedValue(child.filterType, child), cacheGetter, writer, filterContext)
        }
    }
}

/** One bounded reader shared across nesting, so a byte read never recurses through 64 reader wrappers. */
private class FilterProtoBufReader(val source: () -> Byte, val length: Int) {
    var position = 0
    fun read(end: Int): Byte {
        if (position >= end || position >= length) throw ParseException("ProtoBuf filter exceeds declared length")
        position++
        return source()
    }
    fun end(length: Int, parentEnd: Int): Int {
        if (length < 0 || length > parentEnd - position) throw ParseException("ProtoBuf filter exceeds declared length")
        return position + length
    }
}

internal fun <DO : IsFilterList, DM : IsObjectDataModel<DO>> readFilterListProtoBufValues(
    model: DM,
    type: FilterType,
    length: Int,
    reader: () -> Byte,
    context: RequestContext?
): ObjectValues<DO, DM> {
    if (length < 0) throw ParseException("Negative ProtoBuf filter length")
    val filterContext = context ?: RequestContext(DefinitionsContext())
    val continuation = protoBufContinuations.lastOrNull()?.takeIf { !it.consumed && it.reader === reader }
    val earlier = if (continuation != null) {
        if ((continuation.previous as? AbstractValues<*, *>)?.dataModel != model) throw ParseException("Continued ProtoBuf values belong to another model")
        continuation.consumed = true
        @Suppress("UNCHECKED_CAST")
        (continuation.previous.original(1u)?.let(::copyValueWithOwnedContent) as? List<FilterValue>)
    } else null
    val input = FilterProtoBufReader(reader, length)
    val filters = readFilterListProtoBuf(type, input, length, filterContext, earlier)
    val items = MutableValueItems()
    if (filters != null) items[1u] = filters
    return ObjectValues(model, items, filterContext)
}

private fun readFilterListProtoBuf(
    type: FilterType,
    input: FilterProtoBufReader,
    end: Int,
    context: RequestContext?,
    earlier: List<FilterValue>? = null
): List<FilterValue>? = withFilterNesting(context) { filterContext ->
    val definition = filterListProperty(type).filterDefinition()
    var values = earlier?.toMutableList()
    var emptyMarker = false
    var seen = false
    val read = { input.read(end) }
    while (input.position < end) {
        val key = ProtoBuf.readKey(read)
        when (key.tag) {
            1u + EMPTY_COLLECTION_TAG_OFFSET -> {
                if (key.wireType != LENGTH_DELIMITED || ProtoBuf.getLength(key.wireType, read) != 0) {
                    throw ParseException("Empty collection marker must be length-delimited with zero length")
                }
                if (seen) throw ParseException("Empty collection marker conflicts with an earlier value")
                seen = true
                emptyMarker = true
                if (values == null) values = mutableListOf()
            }
            1u -> {
                if (emptyMarker) throw ParseException("Empty collection marker conflicts with another value")
                if (key.wireType != LENGTH_DELIMITED) throw ParseException("Unexpected filter list wire type ${key.wireType}")
                seen = true
                val length = ProtoBuf.getLength(key.wireType, read)
                val typedEnd = input.end(length, end)
                val prefix = mutableListOf<Byte>()
                val childKey = ProtoBuf.readKey { input.read(typedEnd).also { prefix.add(it) } }
                val childType = FilterType.resolve(childKey.tag)
                val child: FilterValue = if (childType != null && childType.isComposite()) {
                    if (childKey.wireType != LENGTH_DELIMITED) throw ParseException("Unexpected composite filter wire type ${childKey.wireType}")
                    var partKey = childKey
                    var children: List<FilterValue>? = null
                    do {
                        if (partKey.tag != childType.index) throw ParseException("Multiple filter values must have the same type")
                        if (partKey.wireType != LENGTH_DELIMITED) throw ParseException("Unexpected composite filter wire type ${partKey.wireType}")
                        val partLength = ProtoBuf.getLength(partKey.wireType) { input.read(typedEnd) }
                        val partEnd = input.end(partLength, typedEnd)
                        children = readFilterListProtoBuf(childType, input, partEnd, filterContext, children)
                        if (input.position >= typedEnd) break
                        partKey = ProtoBuf.readKey { input.read(typedEnd) }
                    } while (true)
                    TypedValue(childType, newFilterList(childType, children))
                } else {
                    var index = 0
                    definition.readTransportBytes(length, {
                        if (index < prefix.size) prefix[index++] else input.read(typedEnd)
                    }, filterContext)
                }
                if (input.position != typedEnd) throw ParseException("ProtoBuf filter did not match declared length")
                if (values == null) values = mutableListOf()
                values.add(child)
            }
            else -> ProtoBuf.skipField(key, read)
        }
    }
    values
}
