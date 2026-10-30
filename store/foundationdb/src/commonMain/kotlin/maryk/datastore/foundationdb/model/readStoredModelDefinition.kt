package maryk.datastore.foundationdb.model

import maryk.foundationdb.TransactionContext
import maryk.core.definitions.Definitions
import maryk.core.models.RootDataModel
import maryk.core.models.migration.StorageFormat
import maryk.core.models.migration.readLegacyTransportStorage
import maryk.core.properties.definitions.contextual.DataModelReference
import maryk.core.query.DefinitionsConversionContext
import maryk.core.query.StorageDefinitionsConversionContext
import maryk.datastore.shared.migration.decodeStorageFormat
import maryk.datastore.shared.StoredModelDefinitionsContext
import maryk.datastore.foundationdb.processors.helpers.awaitResult
import maryk.datastore.foundationdb.processors.helpers.packKey

internal val sourceSchemaStorageFormatKey = "maryk.storage/source-schema-format".encodeToByteArray()

fun readStoredModelDefinition(
    tc: TransactionContext,
    modelPrefix: ByteArray,
    conversionContext: DefinitionsConversionContext,
): RootDataModel<*>? = readStoredModelDefinition(tc, modelPrefix, conversionContext, legacyStorage = true)

internal fun readStoredModelDefinition(
    tc: TransactionContext,
    modelPrefix: ByteArray,
    conversionContext: DefinitionsConversionContext,
    legacyStorage: Boolean,
): RootDataModel<*>? {
    val modelDefKey = packKey(modelPrefix, modelDefinitionKey)
    val dependentsKey = packKey(modelPrefix, modelDependentsDefinitionKey)

    val (storedDataModel, sourceContext) = tc.run { tr ->
        // A retry and every persisted source pair get their own recursive bindings.
        val sourceContext = (conversionContext as? StoredModelDefinitionsContext)?.newSourcePairContext()
            ?: StorageDefinitionsConversionContext()
        val dependentsFuture = tr.get(dependentsKey)
        val modelFuture = tr.get(modelDefKey)

        val dependentBytes = dependentsFuture.awaitResult()
        val modelBytes = modelFuture.awaitResult() ?: return@run null
        fun readPair(): RootDataModel<*> {
            dependentBytes?.let {
                var readIndex = 0
                Definitions.Serializer
                    .readProtoBuf(it.size, { it[readIndex++] }, sourceContext)
                    .toDataObject()
            }
            var readIndex = 0
            return RootDataModel.Model.Serializer
                .readProtoBuf(modelBytes.size, { modelBytes[readIndex++] }, sourceContext)
                .toDataObject()
        }
        val model = if (legacyStorage && decodeStorageFormat(tr.get(packKey(modelPrefix, sourceSchemaStorageFormatKey)).awaitResult()) != StorageFormat.CURRENT) {
            sourceContext.readLegacyTransportStorage(::readPair)
        } else readPair()
        sourceContext.dataModels[model.Meta.name] = DataModelReference(model)
        model to sourceContext
    } ?: return null

    conversionContext.dataModels.putAll(sourceContext.dataModels)
    conversionContext.enums.putAll(sourceContext.enums)
    conversionContext.typeEnums.putAll(sourceContext.typeEnums)
    (conversionContext as? StoredModelDefinitionsContext)?.registerSourceRoot(storedDataModel)

    return storedDataModel
}
