package maryk.datastore.foundationdb.model

import maryk.foundationdb.TransactionContext
import maryk.foundationdb.Transaction
import maryk.core.definitions.Definitions
import maryk.core.definitions.MarykPrimitive
import maryk.core.models.IsRootDataModel
import maryk.core.models.RootDataModel
import maryk.core.protobuf.WriteCache
import maryk.core.query.DefinitionsConversionContext
import maryk.datastore.foundationdb.metadata.ensureModelNameMapping
import maryk.datastore.foundationdb.metadata.toMetadataBytes
import maryk.datastore.foundationdb.processors.helpers.packKey

fun storeModelDefinition(
    tc: TransactionContext,
    metadataPrefix: ByteArray,
    modelId: UInt,
    model: ByteArray,
    dataModel: IsRootDataModel
) {
    val definition = encodeModelDefinition(dataModel)
    tc.run { tr ->
        storeModelDefinition(tr, metadataPrefix, modelId, model, dataModel.Meta.name, definition)
    }
}

internal data class EncodedModelDefinition(
    val name: ByteArray,
    val version: ByteArray,
    val model: ByteArray,
    val dependents: ByteArray?,
)

internal fun encodeModelDefinition(dataModel: IsRootDataModel): EncodedModelDefinition {
    val nameBytes = dataModel.Meta.name.encodeToByteArray()
    val versionBytes = dataModel.Meta.version.toByteArray()

    val context = DefinitionsConversionContext()

    val modelCache = WriteCache()
    val modelSize = RootDataModel.Model.Serializer
        .calculateObjectProtoBufLength(dataModel as RootDataModel<*>, modelCache, context)
    val modelBytes = ByteArray(modelSize).also { arr ->
        var i = 0
        RootDataModel.Model.Serializer.writeObjectProtoBuf(dataModel, modelCache, { b -> arr[i++] = b }, context)
    }

    val dependencies = mutableListOf<MarykPrimitive>()
    dataModel.getAllDependencies(dependencies)

    val dependentsBytes: ByteArray? = if (dependencies.isNotEmpty()) {
        val dependents = Definitions(dependencies)
        val depCache = WriteCache()
        val sz = Definitions.Serializer.calculateObjectProtoBufLength(dependents, depCache, context)
        ByteArray(sz).also { arr ->
            var i = 0
            Definitions.Serializer.writeObjectProtoBuf(dependents, depCache, { b -> arr[i++] = b }, context)
        }
    } else null

    return EncodedModelDefinition(nameBytes, versionBytes, modelBytes, dependentsBytes)
}

internal fun storeModelDefinition(
    transaction: Transaction,
    metadataPrefix: ByteArray,
    modelId: UInt,
    model: ByteArray,
    modelName: String,
    definition: EncodedModelDefinition,
) {
    val modelIdMetadataKey = packKey(metadataPrefix, modelId.toMetadataBytes())
    ensureModelNameMapping(transaction, modelIdMetadataKey, modelName)
    transaction.set(packKey(model, modelNameKey), definition.name)
    transaction.set(packKey(model, modelVersionKey), definition.version)
    transaction.set(packKey(model, modelDefinitionKey), definition.model)
    if (definition.dependents != null) {
        transaction.set(packKey(model, modelDependentsDefinitionKey), definition.dependents)
    }
}
