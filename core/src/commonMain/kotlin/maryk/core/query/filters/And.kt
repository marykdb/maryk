package maryk.core.query.filters

import maryk.core.models.QueryDataModel
import maryk.core.properties.QueryModel
import maryk.core.properties.definitions.InternalMultiTypeDefinition
import maryk.core.properties.definitions.list
import maryk.core.properties.types.TypedValue
import maryk.core.query.RequestContext
import maryk.core.values.ObjectValues
import maryk.json.IsJsonLikeReader
import maryk.json.IsJsonLikeWriter
import maryk.json.JsonToken.StartDocument
import maryk.lib.exceptions.ParseException

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

        override val Model: QueryDataModel<And, Companion> = object : QueryDataModel<And, Companion>(
            Companion,
        ) {
            override fun writeJson(obj: And, writer: IsJsonLikeWriter, context: RequestContext?) {
                filters.writeJsonValue(
                    filters.getPropertyAndSerialize(obj, context)
                        ?: throw ParseException("Missing filters in And filter"),
                    writer,
                    context
                )
            }

            override fun readJson(reader: IsJsonLikeReader, context: RequestContext?): ObjectValues<And, Companion> {
                if (reader.currentToken == StartDocument) {
                    reader.nextToken()
                }

                return this.values(context) {
                    mapNonNulls(
                        filters withSerializable filters.readJson(reader, context)
                    )
                }
            }
        }
    }
}
