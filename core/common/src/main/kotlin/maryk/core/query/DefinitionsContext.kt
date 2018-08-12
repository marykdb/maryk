package maryk.core.query

import maryk.core.models.IsNamedDataModel
import maryk.core.objects.AbstractValues
import maryk.core.properties.IsPropertyContext
import maryk.core.properties.enum.IndexedEnumDefinition

/** Saves the context while writing and parsing Definitions */
open class DefinitionsContext(
    override val dataModels: MutableMap<String, () -> IsNamedDataModel<*>> = mutableMapOf(),
    override val enums: MutableMap<String, IndexedEnumDefinition<*>> = mutableMapOf()
) : IsPropertyContext, ContainsDefinitionsContext {
    private var collectedResults = mutableMapOf<String, AbstractValues<*, *, *>>()

    fun collectResult(collectionName: String, value: AbstractValues<*, *, *>) {
        this.collectedResults.put(collectionName, value)
    }

    fun retrieveResult(collectionName: String) =
        this.collectedResults[collectionName]
}
