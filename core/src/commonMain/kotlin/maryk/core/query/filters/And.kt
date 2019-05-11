package maryk.core.query.filters

import maryk.core.models.QueryDataModel
import maryk.core.properties.ObjectPropertyDefinitions
import maryk.core.properties.definitions.ListDefinition
import maryk.core.properties.definitions.MultiTypeDefinition
import maryk.core.properties.types.TypedValue
import maryk.core.query.RequestContext
import maryk.core.values.ObjectValues
import maryk.json.IsJsonLikeReader
import maryk.json.IsJsonLikeWriter
import maryk.json.JsonToken.StartDocument
import maryk.lib.exceptions.ParseException

/** Does an And comparison against given [filters]. Only if all given filters return true will the entire result be true. */
data class And(
    val filters: List<IsFilter>
) : IsFilter {
    override val filterType = FilterType.And

    constructor(vararg filters: IsFilter) : this(filters.toList())

    object Properties : ObjectPropertyDefinitions<And>() {
        val filters = add(
            1u, "filters",
            ListDefinition(
                valueDefinition = MultiTypeDefinition(
                    typeEnum = FilterType,
                    definitionMap = mapOfFilterDefinitions
                )
            ),
            getter = And::filters,
            toSerializable = { TypedValue(it.filterType, it) },
            fromSerializable = { it.value }
        )
    }

    companion object : QueryDataModel<And, Properties>(
        properties = Properties
    ) {
        override fun invoke(values: ObjectValues<And, Properties>) = And(
            filters = values<List<IsFilter>>(1u)
        )

        override fun writeJson(obj: And, writer: IsJsonLikeWriter, context: RequestContext?) {
            Properties.filters.writeJsonValue(
                Properties.filters.getPropertyAndSerialize(obj, context)
                    ?: throw ParseException("Missing filters in And filter"),
                writer,
                context
            )
        }

        override fun readJson(reader: IsJsonLikeReader, context: RequestContext?): ObjectValues<And, Properties> {
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
