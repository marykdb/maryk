package maryk.core.query.filters

import maryk.core.models.QueryModel
import maryk.core.models.serializers.ObjectDataModelSerializer
import maryk.core.properties.definitions.InternalMultiTypeDefinition
import maryk.core.properties.definitions.list
import maryk.core.properties.definitions.wrapper.IsDefinitionWrapper
import maryk.core.properties.types.TypedValue
import maryk.core.query.RequestContext
import maryk.core.protobuf.WriteCacheReader
import maryk.core.protobuf.WriteCacheWriter
import maryk.core.values.ObjectValues
import maryk.json.IsJsonLikeReader
import maryk.json.IsJsonLikeWriter
import maryk.json.JsonToken.StartDocument
import maryk.lib.exceptions.ParseException

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
            ) {
                withFilterNesting(context) { filterContext ->
                    filters.writeJsonValue(
                        filters.getPropertyAndSerialize(obj, filterContext)
                            ?: throw ParseException("Missing filters in Not filter"),
                        writer,
                        filterContext
                    )
                }
            }

            override fun readJson(reader: IsJsonLikeReader, context: RequestContext?): ObjectValues<Not, Companion> {
                if (reader.currentToken == StartDocument) {
                    reader.nextToken()
                }

                return withFilterNesting(context) { filterContext ->
                    create(filterContext) {
                        filters -= filters.readJson(reader, filterContext)
                    }
                }
            }

            override fun calculateObjectProtoBufLength(dataObject: Not, cacher: WriteCacheWriter, context: RequestContext?) =
                withFilterNesting(context) { filterContext ->
                    super.calculateObjectProtoBufLength(dataObject, cacher, filterContext)
                }

            override fun writeObjectProtoBuf(
                dataObject: Not,
                cacheGetter: WriteCacheReader,
                writer: (byte: Byte) -> Unit,
                context: RequestContext?
            ) = withFilterNesting(context) { filterContext ->
                super.writeObjectProtoBuf(dataObject, cacheGetter, writer, filterContext)
            }

            override fun readProtoBuf(length: Int, reader: () -> Byte, context: RequestContext?) =
                withFilterNesting(context) { filterContext ->
                    super.readProtoBuf(length, reader, filterContext)
                }
        }
    }
}
