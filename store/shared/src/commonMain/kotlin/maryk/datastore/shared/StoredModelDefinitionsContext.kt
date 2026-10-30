package maryk.datastore.shared

import maryk.core.exceptions.DefNotFoundException
import maryk.core.models.IsDataModel
import maryk.core.models.RootDataModel
import maryk.core.properties.definitions.contextual.DataModelReference
import maryk.core.properties.definitions.contextual.IsDataModelReference
import maryk.core.query.DefinitionsContext
import maryk.core.query.DefinitionsConversionContext
import maryk.core.query.StorageDefinitionsConversionContext

/** Source roots shared only after loading persisted definitions, never configured replacements. */
class StoredModelDefinitionsContext : StorageDefinitionsConversionContext() {
    private val sourceRoots = mutableMapOf<String, IsDataModelReference<*>>()

    /** Each pair keeps its own recursive dependencies ahead of the shared root catalogue. */
    fun newSourcePairContext() = StorageDefinitionsConversionContext(
        DefinitionsContext(dataModels = SourcePairModelReferences(sourceRoots))
    )

    fun registerSourceRoot(model: RootDataModel<*>) {
        // Keep the opening baseline when a later migration publishes a replacement.
        sourceRoots.getOrPut(model.Meta.name) { DataModelReference(model) }
    }
}

/** Resolve source reference keys without depending on model ID or name ordering. */
inline fun <I> loadStoredModelDefinitions(
    identities: Iterable<I>,
    readDefinition: (I) -> RootDataModel<*>?,
): Map<I, RootDataModel<*>> {
    val models = linkedMapOf<I, RootDataModel<*>>()
    var pending = identities.toList()
    while (pending.isNotEmpty()) {
        val deferred = mutableListOf<I>()
        var firstFailure: DefNotFoundException? = null
        for (identity in pending) {
            try {
                readDefinition(identity)?.let { models[identity] = it }
            } catch (failure: DefNotFoundException) {
                if (firstFailure == null) firstFailure = failure
                deferred += identity
            }
        }
        if (deferred.size == pending.size) throw firstFailure!!
        pending = deferred
    }
    return models
}

private class SourcePairModelReferences(
    private val sourceRoots: Map<String, IsDataModelReference<*>>,
    private val localReferences: MutableMap<String, IsDataModelReference<*>> = mutableMapOf(),
) : MutableMap<String, IsDataModelReference<*>> by localReferences {
    private val forwardReferences = mutableMapOf<String, IsDataModelReference<*>>()

    override fun get(key: String): IsDataModelReference<*> = localReferences[key]
        ?: forwardReferences.getOrPut(key) {
            object : IsDataModelReference<IsDataModel> {
                override val name = key
                // Captured reference aliases are not definitions and can point
                // back to this forward reference. Only actual local model
                // definitions override the source-root catalogue.
                private fun resolved() = (localReferences[key] as? DataModelReference<*>)
                    ?: sourceRoots[key]
                override val keyLength: Int? get() = resolved()?.keyLength
                override val get: () -> IsDataModel = {
                    resolved()?.get?.invoke()
                        ?: throw DefNotFoundException("Stored source model $key is missing from the source schema catalogue")
                }
            }
        }
}
