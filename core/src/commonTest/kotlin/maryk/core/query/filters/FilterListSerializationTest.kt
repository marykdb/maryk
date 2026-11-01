package maryk.core.query.filters

import maryk.checkJsonConversion
import maryk.checkProtoBufConversion
import maryk.checkYamlConversion
import maryk.core.extensions.bytes.writeVarBytes
import maryk.core.inject.Inject
import maryk.core.models.IsObjectDataModel
import maryk.core.models.TypedObjectDataModel
import maryk.core.models.serializers.IsObjectDataModelSerializer
import maryk.core.models.serializers.ObjectDataModelSerializer
import maryk.core.properties.IsPropertyContext
import maryk.core.properties.definitions.EMPTY_COLLECTION_TAG_OFFSET
import maryk.core.properties.definitions.EmbeddedObjectDefinition
import maryk.core.properties.definitions.InternalMultiTypeDefinition
import maryk.core.properties.definitions.IsChangeableValueDefinition
import maryk.core.properties.definitions.contextual.DataModelReference
import maryk.core.properties.definitions.list
import maryk.core.properties.definitions.wrapper.IsDefinitionWrapper
import maryk.core.properties.references.IsPropertyReference
import maryk.core.properties.types.TypedValue
import maryk.core.protobuf.ProtoBuf
import maryk.core.protobuf.WireType.LENGTH_DELIMITED
import maryk.core.protobuf.WriteCache
import maryk.core.query.RequestContext
import maryk.core.query.pairs.ReferenceValuePair
import maryk.core.query.pairs.with
import maryk.core.values.ObjectValues
import maryk.lib.exceptions.ParseException
import maryk.json.IsJsonLikeWriter
import maryk.json.JsonWriter
import maryk.test.models.SimpleMarykModel
import maryk.yaml.YamlWriter
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class FilterListSerializationTest {
    @Test
    fun mixedCompositeFiltersKeepGenericJsonYamlAndProtoBufEncoding() {
        val value = And(
            Exists(SimpleMarykModel.ref { value }),
            Not(Or(Equals(SimpleMarykModel.ref { value } with "value"), And())),
            Or(Not(), And(Exists(SimpleMarykModel.ref { value })))
        )
        assertEquals(json(value, legacyAnd.Serializer), json(value, And.Serializer))
        assertEquals(yaml(value, legacyAnd.Serializer), yaml(value, And.Serializer))
        assertContentEquals(proto(value, legacyAnd.Serializer), proto(value, And.Serializer))
        checkJsonConversion(value, And, ::newContext)
        checkYamlConversion(value, And, ::newContext)
        checkProtoBufConversion(value, And, ::newContext)
    }

    @Test
    fun compositeProtoBufKeepsInjectionReferencesAndCacheOrder() {
        @Suppress("UNCHECKED_CAST")
        val reference = SimpleMarykModel.ref { value } as
            IsPropertyReference<Any, IsChangeableValueDefinition<Any, IsPropertyContext>, *>
        val value = And(Not(Or(Equals(ReferenceValuePair(reference, Inject("injected"))))))
        val oldContext = newContext()
        val newContext = newContext()
        assertContentEquals(proto(value, legacyAnd.Serializer, oldContext), proto(value, And.Serializer, newContext))
        assertEquals(
            oldContext.collectedInjects?.map { it.reference.completeName },
            newContext.collectedInjects?.map { it.reference.completeName }
        )
        assertEquals(1, newContext.collectedInjects?.size)
    }

    @Test
    fun continuedCompositeBranchesAppendTheirRepeatedFields() {
        val first = Exists(SimpleMarykModel.ref { value })
        val second = Equals(SimpleMarykModel.ref { value } with "value")
        val branch = field(1u, proto(And(first), legacyAnd.Serializer)) +
            field(1u, proto(And(second), legacyAnd.Serializer))
        val bytes = field(1u, branch)
        var index = 0
        assertEquals(Not(And(first, second)), Not.Serializer.readProtoBuf(bytes.size, { bytes[index++] }, newContext()).toDataObject())
        index = 0
        assertEquals(Not(And(first, second)), legacyNot.Serializer.readProtoBuf(bytes.size, { bytes[index++] }, newContext()).toDataObject())
    }

    @Test
    fun continuedEmptyCompositeFragmentsDoNotEraseEarlierFilters() {
        val definition = EmbeddedObjectDefinition(dataModel = { And })
        val full = And(Exists(SimpleMarykModel.ref { value }))
        for ((earlier, incoming) in listOf(full to And(), And() to full)) {
            val bytes = proto(incoming, legacyAnd.Serializer)
            var index = 0
            assertEquals(full, definition.readTransportBytes(bytes.size, { bytes[index++] }, newContext(), earlier))
        }
    }

    @Test
    fun malformedCompositeEnvelopesAndEmptyMarkersAreRejected() {
        val payload = proto(And(Exists(SimpleMarykModel.ref { value })), legacyAnd.Serializer)
        val emptyMarker = field(1u + EMPTY_COLLECTION_TAG_OFFSET, byteArrayOf())
        val invalid = listOf(
            byteArrayOf(8, 1),
            emptyMarker + emptyMarker,
            emptyMarker + payload,
            payload + emptyMarker,
            field(1u, field(FilterType.And.index, payload) + field(FilterType.Or.index, payload)),
            field(1u, field(FilterType.And.index, payload)).dropLast(1).toByteArray()
        )
        for (bytes in invalid) {
            var index = 0
            assertFailsWith<ParseException> { And.Serializer.readProtoBuf(bytes.size, { bytes[index++] }, newContext()) }
            index = 0
            assertFailsWith<ParseException> { legacyAnd.Serializer.readProtoBuf(bytes.size, { bytes[index++] }, newContext()) }
        }
    }

    @Test
    fun maximumMixedNestingRoundTrips() {
        var value: IsFilter = Exists(SimpleMarykModel.ref { value })
        repeat(MAX_FILTER_NESTING_DEPTH) { index ->
            value = when (index % 3) {
                0 -> And(value)
                1 -> Or(value)
                else -> Not(value)
            }
        }
        val filter = value as And
        checkJsonConversion(filter, And, ::newContext)
        checkYamlConversion(filter, And, ::newContext)
        checkProtoBufConversion(filter, And, ::newContext)
    }

    private fun field(index: UInt, bytes: ByteArray) = buildList {
        ProtoBuf.writeKey(index, LENGTH_DELIMITED, ::add)
        bytes.size.writeVarBytes(::add)
        bytes.forEach(::add)
    }.toByteArray()

    private fun <DO : IsFilterList, DM : IsObjectDataModel<DO>> json(
        value: DO,
        serializer: IsObjectDataModelSerializer<DO, DM, RequestContext, RequestContext>
    ) = buildString { serializer.writeObjectAsJson(value, JsonWriter(pretty = true, writer = ::append), newContext()) }

    private fun <DO : IsFilterList, DM : IsObjectDataModel<DO>> yaml(
        value: DO,
        serializer: IsObjectDataModelSerializer<DO, DM, RequestContext, RequestContext>
    ) = buildString { serializer.writeObjectAsJson(value, YamlWriter(writer = ::append), newContext()) }

    private fun <DO : IsFilterList, DM : IsObjectDataModel<DO>> proto(
        value: DO,
        serializer: IsObjectDataModelSerializer<DO, DM, RequestContext, RequestContext>,
        context: RequestContext = newContext()
    ): ByteArray {
        val cache = WriteCache()
        val bytes = ByteArray(serializer.calculateObjectProtoBufLength(value, cache, context))
        var index = 0
        serializer.writeObjectProtoBuf(value, cache, { bytes[index++] = it }, context)
        assertEquals(bytes.size, index)
        return bytes
    }

    private fun newContext() = RequestContext(
        mapOf(SimpleMarykModel.Meta.name to DataModelReference(SimpleMarykModel)),
        dataModel = SimpleMarykModel
    )
}

// Reference models deliberately retain the old generic list/multi-type/embedded
// serialization at every composite level, independent from the production codec.
private val legacyAnd: LegacyFilterModel<And> by lazy { LegacyFilterModel<And> { And(it) } }
private val legacyOr: LegacyFilterModel<Or> by lazy { LegacyFilterModel<Or> { Or(it) } }
private val legacyNot: LegacyFilterModel<Not> by lazy { LegacyFilterModel<Not> { Not(it) } }

private class LegacyFilterModel<DO : IsFilterList>(
    private val factory: (List<IsFilter>) -> DO
) : TypedObjectDataModel<DO, LegacyFilterModel<DO>, RequestContext, RequestContext>() {
    val filters by list(
        index = 1u,
        getter = { value: DO -> value.filters },
        valueDefinition = InternalMultiTypeDefinition(
            typeEnum = FilterType,
            // Do not replace existing entries: Native compares their recursive definitions.
            definitionMap = mapOfFilterDefinitions.mapValues { (type, definition) ->
                when (type) {
                    FilterType.And -> EmbeddedObjectDefinition(dataModel = { legacyAnd })
                    FilterType.Or -> EmbeddedObjectDefinition(dataModel = { legacyOr })
                    FilterType.Not -> EmbeddedObjectDefinition(dataModel = { legacyNot })
                    else -> definition
                }
            }
        ),
        toSerializable = { TypedValue(it.filterType, it) },
        fromSerializable = { it.value }
    )

    override fun invoke(values: ObjectValues<DO, LegacyFilterModel<DO>>): DO = factory(values(1u))

    override val Serializer = object : ObjectDataModelSerializer<DO, LegacyFilterModel<DO>, RequestContext, RequestContext>(this) {
        override fun writeObjectAsJson(
            obj: DO,
            writer: IsJsonLikeWriter,
            context: RequestContext?,
            skip: List<IsDefinitionWrapper<*, *, *, DO>>?
        ) {
            filters.writeJsonValue(requireNotNull(filters.getPropertyAndSerialize(obj, context)), writer, context)
        }
    }
}
