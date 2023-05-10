package maryk.datastore.cassandra.model

import com.datastax.oss.driver.api.core.CqlSession
import com.datastax.oss.driver.api.querybuilder.QueryBuilder.literal
import com.datastax.oss.driver.api.querybuilder.QueryBuilder.selectFrom
import maryk.core.definitions.Definitions
import maryk.core.models.IsRootDataModel
import maryk.core.models.IsStorableDataModel
import maryk.core.models.RootDataModel
import maryk.core.models.migration.MigrationStatus
import maryk.core.properties.types.Version
import maryk.core.query.DefinitionsConversionContext
import maryk.datastore.cassandra.META_Maryk_Model_Definitions

fun checkModelIfMigrationIsNeeded(
    session: CqlSession,
    dataModel: IsRootDataModel,
    onlyCheckVersion: Boolean
): MigrationStatus {
    val metaData = session.execute(
        selectFrom(META_Maryk_Model_Definitions)
            .columns("name","version", "definition", "dependent_definition")
            .whereColumn("name")
            .isEqualTo(literal(dataModel.Meta.name))
            .build()
    ).one() ?: return MigrationStatus.NewModel

    val version = metaData.getByteBuffer("version")?.let {
        Version.Serializer.readFromBytes(it::get)
    }
    return when {
        dataModel.Meta.version != version || !onlyCheckVersion -> {
            val context = DefinitionsConversionContext()

            // Read dependent data models
            metaData.getByteBuffer("dependent_definition")?.let { modelBytes ->
                if (modelBytes.remaining() > 0) {
                    Definitions.Serializer
                        .readProtoBuf(
                            modelBytes.remaining(),
                            modelBytes::get,
                            context
                        ).toDataObject()
                }
            }

            // Read currently stored model definition
            val storedDataModelDefinition = metaData.getByteBuffer("definition")?.let { modelBytes ->
                RootDataModel.Model.Serializer
                    .readProtoBuf(
                        modelBytes.remaining(),
                        modelBytes::get,
                        context
                    ).toDataObject().also { dm ->
                        context.dataModels[dataModel.Meta.name] = { dm }
                    }
            }

            // Check by comparing the data models for if migration is needed
            return dataModel.isMigrationNeeded(storedDataModelDefinition as IsStorableDataModel<*>)
        }
        else -> MigrationStatus.UpToDate
    }
}
