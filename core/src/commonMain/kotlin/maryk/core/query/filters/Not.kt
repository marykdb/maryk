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

/** Reverses the boolean check for given [filter] */
data class Not(
    val filters: List<IsFilter>
) : IsFilter {
    constructor(vararg filters: IsFilter) : this(filters.toList())

    override val filterType = FilterType.Not

    object Properties : ObjectPropertyDefinitions<Not>() {
        val filters = add(1u, "filters",
            ListDefinition(
                valueDefinition = MultiTypeDefinition(
                    typeEnum = FilterType,
                    definitionMap = mapOfFilterDefinitions
                )
            ),
            getter = Not::filters,
            toSerializable = { TypedValue(it.filterType, it) },
            fromSerializable = { it.value as IsFilter }
        )
    }

    companion object : QueryDataModel<Not, Properties>(
        properties = Properties
    ) {
        override fun invoke(values: ObjectValues<Not, Properties>) = Not(
            filters = values<List<IsFilter>>(1u)
        )

        override fun writeJson(obj: Not, writer: IsJsonLikeWriter, context: RequestContext?) {
            Properties.filters.writeJsonValue(
                Properties.filters.getPropertyAndSerialize(obj, context)
                    ?: throw ParseException("Missing filters in Not filter"),
                writer,
                context
            )
        }

        override fun readJson(reader: IsJsonLikeReader, context: RequestContext?): ObjectValues<Not, Properties> {
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
