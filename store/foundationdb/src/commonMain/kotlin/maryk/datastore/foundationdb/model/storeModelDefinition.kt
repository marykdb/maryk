package maryk.datastore.foundationdb.model

import com.apple.foundationdb.TransactionContext
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

    val modelIdMetadataKey = packKey(metadataPrefix, modelId.toMetadataBytes())

    tc.run { tr ->
        ensureModelNameMapping(tr, modelIdMetadataKey, dataModel.Meta.name)
        tr.set(packKey(model, modelNameKey), nameBytes)
        tr.set(packKey(model, modelVersionKey), versionBytes)
        tr.set(packKey(model, modelDefinitionKey), modelBytes)
        if (dependentsBytes != null) {
            tr.set(packKey(model, modelDependentsDefinitionKey), dependentsBytes)
        }
    }
}
