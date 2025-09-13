package maryk.core.query.filters

import maryk.core.models.QueryModel
import maryk.core.models.serializers.ObjectDataModelSerializer
import maryk.core.properties.definitions.InternalMultiTypeDefinition
import maryk.core.properties.definitions.list
import maryk.core.properties.definitions.wrapper.IsDefinitionWrapper
import maryk.core.properties.types.TypedValue
import maryk.core.query.RequestContext
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
                filters.writeJsonValue(
                    filters.getPropertyAndSerialize(obj, context)
                        ?: throw ParseException("Missing filters in Not filter"),
                    writer,
                    context
                )
            }

            override fun readJson(reader: IsJsonLikeReader, context: RequestContext?): ObjectValues<Not, Companion> {
                if (reader.currentToken == StartDocument) {
                    reader.nextToken()
                }

                return create(context) {
                    filters -= filters.readJson(reader, context)
                }
            }
        }
    }
}
