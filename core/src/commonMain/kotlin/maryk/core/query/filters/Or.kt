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
            Model.invoke(values)

        override val Model: QueryDataModel<Or, Companion> = object : QueryDataModel<Or, Companion>(
            properties = Companion,
        ) {
            override fun invoke(values: ObjectValues<Or, Companion>) = Or(
                filters = values<List<IsFilter>>(1u)
            )

            override fun writeJson(obj: Or, writer: IsJsonLikeWriter, context: RequestContext?) {
                filters.writeJsonValue(
                    filters.getPropertyAndSerialize(obj, context)
                        ?: throw ParseException("Missing filters in Or"),
                    writer,
                    context
                )
            }

            override fun readJson(reader: IsJsonLikeReader, context: RequestContext?): ObjectValues<Or, Companion> {
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
