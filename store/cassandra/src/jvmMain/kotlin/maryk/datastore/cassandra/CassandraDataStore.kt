package maryk.datastore.cassandra

import com.datastax.oss.driver.api.core.CqlSession
import com.datastax.oss.driver.api.core.CqlSessionBuilder
import com.datastax.oss.driver.api.core.type.DataTypes
import com.datastax.oss.driver.api.querybuilder.SchemaBuilder
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.launch
import maryk.core.clock.HLC
import maryk.core.exceptions.TypeException
import maryk.core.models.IsRootDataModel
import maryk.core.models.migration.MigrationException
import maryk.core.models.migration.MigrationHandler
import maryk.core.models.migration.MigrationStatus
import maryk.core.models.migration.StoredRootDataModelDefinition
import maryk.core.query.requests.AddRequest
import maryk.datastore.cassandra.model.checkModelIfMigrationIsNeeded
import maryk.datastore.cassandra.model.createModelDefinitionTableIfNeeded
import maryk.datastore.cassandra.model.storeModelDefinition
import maryk.datastore.cassandra.processors.AnyAddStoreAction
import maryk.datastore.cassandra.processors.processAddRequest
import maryk.datastore.shared.AbstractDataStore

internal const val META_Maryk_Model_Definitions = "Meta_Maryk_Model_Definitions"

/**
 * DataProcessor that stores all data changes in local memory.
 * Very useful for tests.
 */
class CassandraDataStore(
    sessionBuilder: CqlSessionBuilder,
    val keyspace: String,
    override val keepAllVersions: Boolean = false,
    dataModelsById: Map<UInt, IsRootDataModel>,
    private val onlyCheckModelVersion: Boolean = false,
    migrationHandler: MigrationHandler<CassandraDataStore>? = null
) : AbstractDataStore(dataModelsById) {
    internal val session: CqlSession

    init {
        session = run {
            val tempSession = sessionBuilder.build()

            val createKeyspace = SchemaBuilder.createKeyspace(keyspace)
                .ifNotExists()
                .withSimpleStrategy(3)

            tempSession.execute(createKeyspace.build())
            tempSession.close()

            sessionBuilder.withKeyspace(keyspace).build()
        }

        createModelDefinitionTableIfNeeded(session)

        for (dataModel in dataModelsById.values) {
            when (val migrationStatus = checkModelIfMigrationIsNeeded(session, dataModel, this.onlyCheckModelVersion)) {
                MigrationStatus.UpToDate -> Unit // Do nothing since no work is needed
                MigrationStatus.NewModel -> {
                    // Model updated so can be stored
                    val newTable = SchemaBuilder.createTable(dataModel.Meta.name)
                        .ifNotExists().withPartitionKey("id", DataTypes.TEXT)

                    session.execute(newTable.build())
                    storeModelDefinition(dataModel)
                }
                MigrationStatus.OnlySafeAdds -> {
                    // Model updated so can be stored
                    storeModelDefinition(dataModel)
                }
                is MigrationStatus.NewIndicesOnExistingProperties -> {
//                    fillIndex(migrationStatus.indicesToIndex, tableColumnFamilies)
                    TODO("UPDATE INDEX DEF")
                    storeModelDefinition(dataModel)
                }
                is MigrationStatus.NeedsMigration -> {
                    val succeeded = migrationHandler?.invoke(this, migrationStatus.storedDataModel as StoredRootDataModelDefinition, dataModel)
                        ?: throw MigrationException("Migration needed: No migration handler present. \n$migrationStatus")

                    if (!succeeded) {
                        throw MigrationException("Migration could not be handled for ${dataModel.Meta.name} & ${(migrationStatus.storedDataModel as? StoredRootDataModelDefinition)?.Meta?.version}")
                    }

                    migrationStatus.indicesToIndex?.let {
//                        fillIndex(it, tableColumnFamilies)
                        TODO("UPDATE INDEX DEF")
                    }

                    // Successful so store new model definition
                    storeModelDefinition(dataModel)
                }
            }
        }
    }

    init {
        startFlows()
    }

    @Suppress("UNCHECKED_CAST")
    override fun startFlows() {
        super.startFlows()

        this.launch {
            var clock = HLC()

            storeFlow
                .onStart { storeActorHasStarted.complete(Unit) }
                .collect { storeAction ->
                    try {
                        clock = clock.calculateMaxTimeStamp()

                        when (storeAction.request) {
                            is AddRequest<*> ->
                                processAddRequest(clock, storeAction as AnyAddStoreAction, this@CassandraDataStore, updateSharedFlow)
                            else -> throw TypeException("Unknown request type ${storeAction.request}")
                        }
                    } catch (e: Throwable) {
                        storeAction.response.completeExceptionally(e)
                    }
                }
        }
    }

    override fun close() {
        session.close()
        super.close()
    }
}
