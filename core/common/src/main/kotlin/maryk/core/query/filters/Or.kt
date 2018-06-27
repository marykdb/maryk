package maryk.core.query.filters

import maryk.core.models.QueryDataModel
import maryk.core.objects.ValueMap
import maryk.core.properties.definitions.ListDefinition
import maryk.core.properties.definitions.MultiTypeDefinition
import maryk.core.properties.definitions.PropertyDefinitions
import maryk.core.properties.types.TypedValue
import maryk.core.query.DataModelPropertyContext
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

    internal object Properties : PropertyDefinitions<Or>() {
        val filters = add(0, "filters",
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

    internal companion object: QueryDataModel<Or, Properties>(
        properties = Properties
    ) {
        override fun invoke(map: ValueMap<Or>) = Or(
            filters = map<List<IsFilter>>(0)
        )

        override fun writeJson(map: ValueMap<Or>, writer: IsJsonLikeWriter, context: DataModelPropertyContext?) {
            @Suppress("UNCHECKED_CAST")
            Properties.filters.writeJsonValue(
                map[Properties.filters.index] as List<TypedValue<FilterType, Any>>? ?: throw ParseException("Missing filters in Or"),
                writer,
                context
            )
        }

        override fun writeJson(obj: Or, writer: IsJsonLikeWriter, context: DataModelPropertyContext?) {
            Properties.filters.writeJsonValue(
                Properties.filters.getPropertyAndSerialize(obj, context) ?: throw ParseException("Missing filters in Or"),
                writer,
                context
            )
        }

        override fun readJson(reader: IsJsonLikeReader, context: DataModelPropertyContext?): ValueMap<Or> {
            if (reader.currentToken == JsonToken.StartDocument){
                reader.nextToken()
            }

            return this.map{
                mapOf(
                    filters with filters.readJson(reader, context)
                )
            }
        }
    }
}
