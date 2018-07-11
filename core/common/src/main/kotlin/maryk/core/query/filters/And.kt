package maryk.core.query.filters

import maryk.core.models.QueryDataModel
import maryk.core.objects.ObjectValues
import maryk.core.properties.definitions.ListDefinition
import maryk.core.properties.definitions.MultiTypeDefinition
import maryk.core.properties.ObjectPropertyDefinitions
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

    internal object Properties : ObjectPropertyDefinitions<And>() {
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

    internal companion object: QueryDataModel<And, Properties>(
        properties = Properties
    ) {
        override fun invoke(map: ObjectValues<And, Properties>) = And(
            filters = map<List<IsFilter>>(0)
        )

        override fun writeJson(map: ObjectValues<And, Properties>, writer: IsJsonLikeWriter, context: DataModelPropertyContext?) {
            Properties.filters.writeJsonValue(
                map.original { filters } ?: throw ParseException("Missing filters in And filter"),
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

        override fun readJson(reader: IsJsonLikeReader, context: DataModelPropertyContext?): ObjectValues<And, Properties> {
            if (reader.currentToken == JsonToken.StartDocument){
                reader.nextToken()
            }

            return this.map {
                mapOf(
                    filters with filters.readJson(reader, context)
                )
            }
        }
    }
}
