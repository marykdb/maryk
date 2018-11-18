package maryk.core.query.filters

import maryk.core.models.QueryDataModel
import maryk.core.values.ObjectValues
import maryk.core.properties.ObjectPropertyDefinitions
import maryk.core.properties.definitions.ListDefinition
import maryk.core.properties.definitions.MultiTypeDefinition
import maryk.core.properties.types.TypedValue
import maryk.core.query.RequestContext
import maryk.json.IsJsonLikeReader
import maryk.json.IsJsonLikeWriter
import maryk.json.JsonToken
import maryk.lib.exceptions.ParseException

/** Does an Or comparison against given [filters]. If one returns true the entire result will be true. */
data class Or(
    val filters: List<IsFilter>
) : IsFilter {
    override val filterType = FilterType.Or

    constructor(vararg filters: IsFilter) : this(filters.toList())

    object Properties : ObjectPropertyDefinitions<Or>() {
        val filters = add(1, "filters",
            ListDefinition(
                valueDefinition = MultiTypeDefinition(
                    typeEnum = FilterType,
                    definitionMap = mapOfFilterDefinitions
                )
            ),
            getter = Or::filters,
            toSerializable = { TypedValue(it.filterType, it) },
            fromSerializable = { it.value as IsFilter }
        )
    }

    companion object: QueryDataModel<Or, Properties>(
        properties = Properties
    ) {
        override fun invoke(values: ObjectValues<Or, Properties>) = Or(
            filters = values<List<IsFilter>>(1)
        )

        override fun writeJson(obj: Or, writer: IsJsonLikeWriter, context: RequestContext?) {
            Properties.filters.writeJsonValue(
                Properties.filters.getPropertyAndSerialize(obj, context) ?: throw ParseException("Missing filters in Or"),
                writer,
                context
            )
        }

        override fun readJson(reader: IsJsonLikeReader, context: RequestContext?): ObjectValues<Or, Properties> {
            if (reader.currentToken == JsonToken.StartDocument){
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
