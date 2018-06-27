package maryk.core.query.filters

import maryk.core.models.QueryDataModel
import maryk.core.properties.definitions.ListDefinition
import maryk.core.properties.definitions.MultiTypeDefinition
import maryk.core.properties.definitions.PropertyDefinitions
import maryk.core.properties.types.TypedValue
import maryk.core.query.DataModelPropertyContext
import maryk.json.IsJsonLikeReader
import maryk.json.IsJsonLikeWriter
import maryk.json.JsonToken
import maryk.lib.exceptions.ParseException

/** Does an And comparison against given [filters]. Only if all given filters return true will the entire result be true. */
data class And(
    val filters: List<IsFilter>
) : IsFilter {
    override val filterType = FilterType.And

    constructor(vararg filters: IsFilter) : this(filters.toList())

    internal object Properties : PropertyDefinitions<And>() {
        val filters = add(0, "filters",
            ListDefinition(
                valueDefinition = MultiTypeDefinition(
                    typeEnum = FilterType,
                    definitionMap = mapOfFilterDefinitions
                )
            ),
            getter = And::filters,
            toSerializable = { TypedValue(it.filterType, it) },
            fromSerializable = { it.value as IsFilter }
        )
    }

    internal companion object: QueryDataModel<And>(
        properties = Properties
    ) {
        override fun invoke(map: Map<Int, *>) = And(
            filters = map<List<IsFilter>>(0)
        )

        override fun writeJson(map: Map<Int, Any>, writer: IsJsonLikeWriter, context: DataModelPropertyContext?) {
            @Suppress("UNCHECKED_CAST")
            Properties.filters.writeJsonValue(
                map[Properties.filters.index] as List<TypedValue<FilterType, Any>>? ?: throw ParseException("Missing filters in And filter"),
                writer,
                context
            )
        }

        override fun writeJson(obj: And, writer: IsJsonLikeWriter, context: DataModelPropertyContext?) {
            Properties.filters.writeJsonValue(
                Properties.filters.getPropertyAndSerialize(obj, context) ?: throw ParseException("Missing filters in And filter"),
                writer,
                context
            )
        }

        override fun readJson(reader: IsJsonLikeReader, context: DataModelPropertyContext?): Map<Int, Any> {
            if (reader.currentToken == JsonToken.StartDocument){
                reader.nextToken()
            }

            return mapOf(
                Properties.filters.index to Properties.filters.readJson(reader, context)
            )
        }
    }
}
