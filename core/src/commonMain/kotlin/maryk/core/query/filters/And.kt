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

/** Does an And comparison against given [filters]. Only if all given filters return true will the entire result be true. */
data class And(
    override val filters: List<IsFilter>
) : IsFilterList {
    override val filterType = FilterType.And

    constructor(vararg filters: IsFilter) : this(filters.toList())

    companion object : QueryModel<And, Companion>() {
        val filters by list(
            index = 1u,
            getter = And::filters,
            valueDefinition = InternalMultiTypeDefinition(
                typeEnum = FilterType,
                definitionMap = mapOfFilterDefinitions
            ),
            toSerializable = { TypedValue(it.filterType, it) },
            fromSerializable = { it.value }
        )

        override fun invoke(values: ObjectValues<And, Companion>) =
            And(
                filters = values<List<IsFilter>>(1u)
            )

        override val Serializer = object: ObjectDataModelSerializer<And, Companion, RequestContext, RequestContext>(this) {
            override fun writeObjectAsJson(
                obj: And,
                writer: IsJsonLikeWriter,
                context: RequestContext?,
                skip: List<IsDefinitionWrapper<*, *, *, And>>?
            ) = writeFilterListJson(obj, writer, context)

            override fun readJson(reader: IsJsonLikeReader, context: RequestContext?): ObjectValues<And, Companion> {
                val filterContext = context ?: RequestContext(DefinitionsContext())
                return create(filterContext) {
                    filters -= readFilterListJson(FilterType.And, reader, filterContext)
                }
            }

            override fun calculateObjectProtoBufLength(dataObject: And, cacher: WriteCacheWriter, context: RequestContext?) =
                calculateFilterListProtoBufLength(dataObject, cacher, context)

            override fun writeObjectProtoBuf(
                dataObject: And,
                cacheGetter: WriteCacheReader,
                writer: (byte: Byte) -> Unit,
                context: RequestContext?
            ) = writeFilterListProtoBuf(dataObject, cacheGetter, writer, context)

            override fun readProtoBuf(length: Int, reader: () -> Byte, context: RequestContext?) =
                readFilterListProtoBufValues(model, FilterType.And, length, reader, context)
        }
    }
}
