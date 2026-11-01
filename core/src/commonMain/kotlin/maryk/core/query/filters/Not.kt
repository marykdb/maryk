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

/** Reverses the boolean check for given [filter] */
data class Not(
    override val filters: List<IsFilter>
) : IsFilterList {
    constructor(vararg filters: IsFilter) : this(filters.toList())

    override val filterType = FilterType.Not

    companion object : QueryModel<Not, Companion>() {
        val filters by list(
            1u,
            getter = Not::filters,
            valueDefinition = InternalMultiTypeDefinition(
                typeEnum = FilterType,
                definitionMap = mapOfFilterDefinitions
            ),
            toSerializable = { TypedValue(it.filterType, it) },
            fromSerializable = { it.value }
        )

        override fun invoke(values: ObjectValues<Not, Companion>): Not =
            Not(
                filters = values<List<IsFilter>>(1u)
            )

        override val Serializer = object: ObjectDataModelSerializer<Not, Companion, RequestContext, RequestContext>(this) {
            override fun writeObjectAsJson(
                obj: Not,
                writer: IsJsonLikeWriter,
                context: RequestContext?,
                skip: List<IsDefinitionWrapper<*, *, *, Not>>?
            ) = writeFilterListJson(obj, writer, context)

            override fun readJson(reader: IsJsonLikeReader, context: RequestContext?): ObjectValues<Not, Companion> {
                val filterContext = context ?: RequestContext(DefinitionsContext())
                return create(filterContext) {
                    filters -= readFilterListJson(FilterType.Not, reader, filterContext)
                }
            }

            override fun calculateObjectProtoBufLength(dataObject: Not, cacher: WriteCacheWriter, context: RequestContext?) =
                calculateFilterListProtoBufLength(dataObject, cacher, context)

            override fun writeObjectProtoBuf(
                dataObject: Not,
                cacheGetter: WriteCacheReader,
                writer: (byte: Byte) -> Unit,
                context: RequestContext?
            ) = writeFilterListProtoBuf(dataObject, cacheGetter, writer, context)

            override fun readProtoBuf(length: Int, reader: () -> Byte, context: RequestContext?) =
                readFilterListProtoBufValues(model, FilterType.Not, length, reader, context)
        }
    }
}
