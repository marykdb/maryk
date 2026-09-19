package maryk.core.properties.definitions

import maryk.core.properties.IsPropertyContext
import maryk.json.IsJsonLikeReader
import maryk.json.IsJsonLikeWriter
import maryk.json.JsonToken.NullValue
import maryk.json.JsonToken.Value

/**
 * Definition for a List property which writes a single value instead of list when only 1 available.
 * Only for internal use
 */
internal data class SingleOrListDefinition<T : Any, CX : IsPropertyContext> internal constructor(
    override val required: Boolean = true,
    override val final: Boolean = false,
    override val minSize: UInt? = null,
    override val maxSize: UInt? = null,
    override val valueDefinition: IsValueDefinition<T, CX>,
    override val default: List<T>? = null
) : IsListDefinition<T, CX>,
    IsUsableInMapValue<List<T>, CX>,
    IsUsableInMultiType<List<T>, CX> {

    init {
        require(valueDefinition.required) { "Definition for value should have required=true on List" }
    }

    override fun readJson(reader: IsJsonLikeReader, context: CX?) =
        if (reader.currentToken !is NullValue && reader.currentToken is Value<*>) {
            listOf(
                this.valueDefinition.readJson(reader, context)
            )
        } else {
            super.readJson(reader, context)
        }

    override fun writeJsonValue(value: List<T>, writer: IsJsonLikeWriter, context: CX?) {
        if (value.size == 1) {
            this.valueDefinition.writeJsonValue(value.first(), writer, context)
        } else {
            super.writeJsonValue(value, writer, context)
        }
    }
}
