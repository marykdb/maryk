package maryk.datastore.foundationdb

import com.apple.foundationdb.TransactionContext
import maryk.core.definitions.Definitions
import maryk.core.exceptions.StorageException
import maryk.core.models.IsRootDataModel
import maryk.core.models.RootDataModel
import maryk.core.models.migration.MigrationStatus
import maryk.core.models.migration.MigrationStatus.NewModel
import maryk.core.models.migration.MigrationStatus.UpToDate
import maryk.core.properties.definitions.contextual.DataModelReference
import maryk.core.properties.types.Version
import maryk.core.query.DefinitionsConversionContext
import maryk.datastore.foundationdb.model.modelDefinitionKey
import maryk.datastore.foundationdb.model.modelDependentsDefinitionKey
import maryk.datastore.foundationdb.model.modelNameKey
import maryk.datastore.foundationdb.model.modelVersionKey
import maryk.datastore.foundationdb.processors.helpers.packKey

fun checkModelIfMigrationIsNeeded(
    tc: TransactionContext,
    model: ByteArray,
    dataModel: IsRootDataModel,
    onlyCheckModelVersion: Boolean,
    conversionContext: DefinitionsConversionContext
): MigrationStatus {
    val nameKey = packKey(model, modelNameKey)
    val versionKey = packKey(model, modelVersionKey)
    val modelDefKey = packKey(model, modelDefinitionKey)
    val dependentsDefKey = packKey(model, modelDependentsDefinitionKey)

    // Read name and version within a single transaction
    val (name, version) = tc.run { tr ->
        val nameF = tr.get(nameKey)
        val versionF = tr.get(versionKey)
        val nameBytes = nameF.join()
        val versionBytes = versionF.join()

        val readName = nameBytes?.decodeToString()
        val readVersion = versionBytes?.let { bytes ->
            var i = 0
            Version.Serializer.readFromBytes { bytes[i++] }
        }
        readName to readVersion
    }

    if (name == null || version == null) {
        return NewModel
    }

    return if (dataModel.Meta.version != version || !onlyCheckModelVersion) {
        // Ensure dependent model definitions are available in the conversion context
        tc.run { tr ->
            val depBytes = tr.get(dependentsDefKey).join()
            if (depBytes != null) {
                var readIndex = 0
                Definitions.Serializer
                    .readProtoBuf(depBytes.size, { depBytes[readIndex++] }, conversionContext)
                    .toDataObject()
            }
        }

        // Load stored model definition
        val storedDataModel = tc.run { tr ->
            val modelBytes = tr.get(modelDefKey).join()
                ?: throw StorageException("Model is unexpectedly missing in metadata for ${dataModel.Meta.name}")

            var readIndex = 0
            RootDataModel.Model.Serializer
                .readProtoBuf(modelBytes.size, { modelBytes[readIndex++] }, conversionContext)
                .toDataObject()
        }

        conversionContext.dataModels[dataModel.Meta.name] = DataModelReference(storedDataModel)

        // Defer to the model diff to determine migration status
        dataModel.isMigrationNeeded(storedDataModel)
    } else {
        UpToDate
    }
}
