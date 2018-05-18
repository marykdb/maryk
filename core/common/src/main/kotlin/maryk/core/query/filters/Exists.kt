package maryk.core.query.filters

import maryk.core.objects.QueryDataModel
import maryk.core.properties.IsPropertyContext
import maryk.core.properties.definitions.PropertyDefinitions
import maryk.core.properties.definitions.wrapper.IsValuePropertyDefinitionWrapper
import maryk.core.properties.references.IsPropertyReference
import maryk.core.query.DataModelPropertyContext
import maryk.json.IsJsonLikeReader
import maryk.json.IsJsonLikeWriter
import maryk.json.JsonToken
import maryk.lib.exceptions.ParseException

/** Checks if value exists */
fun <T: Any> IsPropertyReference<T, IsValuePropertyDefinitionWrapper<T, *, IsPropertyContext, *>>.exists() =
        Exists(this)

/** Checks if [reference] to value of type [T] exists */
data class Exists<T: Any> internal constructor(
    override val reference: IsPropertyReference<T, IsValuePropertyDefinitionWrapper<T, *, IsPropertyContext, *>>
) : IsPropertyCheck<T> {
    override val filterType = FilterType.Exists

    internal object Properties : PropertyDefinitions<Exists<*>>() {
        val reference = IsPropertyCheck.addReference(this, Exists<*>::reference)
    }

    internal companion object: QueryDataModel<Exists<*>>(
        properties = Properties
    ) {
        override fun invoke(map: Map<Int, *>) = Exists<Any>(
            reference = map(0)
        )

        override fun writeJson(map: Map<Int, Any>, writer: IsJsonLikeWriter, context: DataModelPropertyContext?) {
            writer.writeString(
                Properties.reference.definition.asString(map(0))
            )
        }

        override fun writeJson(obj: Exists<*>, writer: IsJsonLikeWriter, context: DataModelPropertyContext?) {
            writer.writeString(
                Properties.reference.definition.asString(obj.reference)
            )
        }

        override fun readJson(reader: IsJsonLikeReader, context: DataModelPropertyContext?): Map<Int, Any> {
            var currentToken = reader.currentToken

            if (currentToken == JsonToken.StartDocument){
                currentToken = reader.nextToken()

                if (currentToken is JsonToken.Suspended) {
                    currentToken = currentToken.lastToken
                }
            }

            @Suppress("UNCHECKED_CAST")
            (currentToken as? JsonToken.Value<String>)?.let {
                return mapOf(
                    Properties.reference.index to Properties.reference.definition.fromString(it.value, context)
                )
            } ?: throw ParseException("Expected only a property reference in Exists filter")
        }
    }
}
