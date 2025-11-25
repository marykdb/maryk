package maryk.datastore.foundationdb.model

import maryk.foundationdb.TransactionContext
import maryk.core.definitions.Definitions
import maryk.core.models.RootDataModel
import maryk.core.properties.definitions.contextual.DataModelReference
import maryk.core.query.DefinitionsConversionContext
import maryk.datastore.foundationdb.FoundationDBDataStore
import maryk.datastore.foundationdb.processors.helpers.awaitResult
import maryk.datastore.foundationdb.processors.helpers.packKey

fun readStoredModelDefinition(
    tc: TransactionContext,
    modelPrefix: ByteArray,
    conversionContext: DefinitionsConversionContext,
): RootDataModel<*>? {
    val modelDefKey = packKey(modelPrefix, modelDefinitionKey)
    val dependentsKey = packKey(modelPrefix, modelDependentsDefinitionKey)

    val storedDataModel = tc.run { tr ->
        val dependentsFuture = tr.get(dependentsKey)
        val modelFuture = tr.get(modelDefKey)

        dependentsFuture.awaitResult()?.let { dependentBytes ->
            var readIndex = 0
            Definitions.Serializer
                .readProtoBuf(dependentBytes.size, { dependentBytes[readIndex++] }, conversionContext)
                .toDataObject()
        }

        val modelBytes = modelFuture.awaitResult() ?: return@run null

        var readIndex = 0
        RootDataModel.Model.Serializer
            .readProtoBuf(modelBytes.size, { modelBytes[readIndex++] }, conversionContext)
            .toDataObject()
    } ?: return null

    conversionContext.dataModels[storedDataModel.Meta.name] = DataModelReference(storedDataModel)

    return storedDataModel
}
