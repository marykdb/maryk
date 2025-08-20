package maryk.datastore.foundationdb.model

import com.apple.foundationdb.TransactionContext
import com.apple.foundationdb.directory.DirectorySubspace
import maryk.core.definitions.Definitions
import maryk.core.definitions.MarykPrimitive
import maryk.core.models.IsRootDataModel
import maryk.core.models.RootDataModel
import maryk.core.protobuf.WriteCache
import maryk.core.query.DefinitionsConversionContext

fun storeModelDefinition(
    tc: TransactionContext,
    model: DirectorySubspace,
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

    tc.run { tr ->
        tr.set(model.pack(modelNameKey), nameBytes)
        tr.set(model.pack(modelVersionKey), versionBytes)
        tr.set(model.pack(modelDefinitionKey), modelBytes)
        if (dependentsBytes != null) {
            tr.set(model.pack(modelDependentsDefinitionKey), dependentsBytes)
        }
    }
}
