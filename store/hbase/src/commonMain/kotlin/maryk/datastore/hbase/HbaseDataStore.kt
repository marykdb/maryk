@file:Suppress("UNUSED_PARAMETER")

package maryk.datastore.hbase

import kotlinx.coroutines.flow.Flow
import maryk.core.models.IsRootDataModel
import maryk.core.models.migration.MigrationHandler
import maryk.core.models.migration.VersionUpdateHandler
import maryk.core.query.requests.IsFetchRequest
import maryk.core.query.requests.IsStoreRequest
import maryk.core.query.responses.IsDataResponse
import maryk.core.query.responses.IsResponse
import maryk.core.query.responses.UpdateResponse
import maryk.core.query.responses.updates.IsUpdateResponse
import maryk.core.query.responses.updates.ProcessResponse
import maryk.datastore.shared.AbstractDataStore
import org.apache.hadoop.hbase.client.Connection

class HbaseDataStore(
    override val keepAllVersions: Boolean = true,
    connection: Connection,
    dataModelsById: Map<UInt, IsRootDataModel>,
    private val onlyCheckModelVersion: Boolean = false,
    val migrationHandler: MigrationHandler<HbaseDataStore>? = null,
    val versionUpdateHandler: VersionUpdateHandler<HbaseDataStore>? = null,
): AbstractDataStore(dataModelsById) {
    override suspend fun <DM : IsRootDataModel, RQ : IsStoreRequest<DM, RP>, RP : IsResponse> execute(request: RQ): RP {
        TODO("Not yet implemented")
    }

    override suspend fun <DM : IsRootDataModel, RQ : IsFetchRequest<DM, RP>, RP : IsDataResponse<DM>> executeFlow(
        request: RQ
    ): Flow<IsUpdateResponse<DM>> {
        TODO("Not yet implemented")
    }

    override suspend fun <DM : IsRootDataModel> processUpdate(updateResponse: UpdateResponse<DM>): ProcessResponse<DM> {
        TODO("Not yet implemented")
    }

    override fun close() {
    }

    override suspend fun closeAllListeners() {
        TODO("Not yet implemented")
    }
}
