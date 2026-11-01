package maryk.core.query.filters

import maryk.core.models.QueryModel
import maryk.core.models.serializers.ObjectDataModelSerializer
import maryk.core.properties.definitions.InternalMultiTypeDefinition
import maryk.core.properties.definitions.list
import maryk.core.properties.definitions.wrapper.IsDefinitionWrapper
import maryk.core.properties.types.TypedValue
import maryk.core.query.DefinitionsContext
import maryk.core.query.RequestContext
import maryk.core.protobuf.WriteCacheReader
import maryk.core.protobuf.WriteCacheWriter
import maryk.core.values.ObjectValues
import maryk.json.IsJsonLikeReader
import maryk.json.IsJsonLikeWriter

/** Does an Or comparison against given [filters]. If one returns true the entire result will be true. */
data class Or(
    override val filters: List<IsFilter>
) : IsFilterList {
    override val filterType = FilterType.Or

    constructor(vararg filters: IsFilter) : this(filters.toList())

    companion object : QueryModel<Or, Companion>() {
        val filters by list(
            index = 1u,
            getter = Or::filters,
            valueDefinition = InternalMultiTypeDefinition(
                typeEnum = FilterType,
                definitionMap = mapOfFilterDefinitions
            ),
            toSerializable = { TypedValue(it.filterType, it) },
            fromSerializable = { it.value }
        )

        override fun invoke(values: ObjectValues<Or, Companion>) =
            Or(
                filters = values<List<IsFilter>>(1u)
            )

        override val Serializer = object: ObjectDataModelSerializer<Or, Companion, RequestContext, RequestContext>(this) {
            override fun writeObjectAsJson(
                obj: Or,
                writer: IsJsonLikeWriter,
                context: RequestContext?,
                skip: List<IsDefinitionWrapper<*, *, *, Or>>?
            ) = writeFilterListJson(obj, writer, context)

            override fun readJson(reader: IsJsonLikeReader, context: RequestContext?): ObjectValues<Or, Companion> {
                val filterContext = context ?: RequestContext(DefinitionsContext())
                return create(filterContext) {
                    filters -= readFilterListJson(FilterType.Or, reader, filterContext)
                }
            }

            override fun calculateObjectProtoBufLength(dataObject: Or, cacher: WriteCacheWriter, context: RequestContext?) =
                calculateFilterListProtoBufLength(dataObject, cacher, context)

            override fun writeObjectProtoBuf(
                dataObject: Or,
                cacheGetter: WriteCacheReader,
                writer: (byte: Byte) -> Unit,
                context: RequestContext?
            ) = writeFilterListProtoBuf(dataObject, cacheGetter, writer, context)

            override fun readProtoBuf(length: Int, reader: () -> Byte, context: RequestContext?) =
                readFilterListProtoBufValues(model, FilterType.Or, length, reader, context)
        }
    }
}
