package maryk.core.models.serializers

import maryk.core.properties.IsPropertyContext
import maryk.core.values.IsValues
import maryk.json.IsJsonLikeReader
import maryk.json.IsJsonLikeWriter

interface IsJsonSerializer<V: IsValues<*>, CX: IsPropertyContext> {
    /**
     * Write [values] for this DataModel to JSON
     * Optionally pass a [context] when needed for more complex property types
     */
    fun writeJson(
        values: V,
        context: CX? = null,
        pretty: Boolean = false
    ): String

    /**
     * Write [values] for this DataModel to JSON with [writer]
     * Optionally pass a [context] when needed for more complex property types
     */
    fun writeJson(
        values: V,
        writer: IsJsonLikeWriter,
        context: CX? = null
    )

    /**
     * Read JSON from [json] to a Map with values
     * Optionally pass a [context] when needed to read more complex property types
     */
    fun readJson(json: String, context: IsPropertyContext? = null): V

    /**
     * Read JSON from [reader] to a Map with values
     * Optionally pass a [context] when needed to read more complex property types
     */
    fun readJson(reader: IsJsonLikeReader, context: IsPropertyContext? = null): V
}
