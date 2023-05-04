package maryk.datastore.cassandra

import com.datastax.oss.driver.api.core.CqlSession
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.launch
import maryk.core.clock.HLC
import maryk.core.exceptions.TypeException
import maryk.core.models.IsRootDataModel
import maryk.datastore.shared.AbstractDataStore

/**
 * DataProcessor that stores all data changes in local memory.
 * Very useful for tests.
 */
class CassandraDataStore(
    val session: CqlSession,
    override val keepAllVersions: Boolean = false,
    dataModelsById: Map<UInt, IsRootDataModel>
) : AbstractDataStore(dataModelsById) {
    init {
        startFlows()
    }

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
                            else -> throw TypeException("Unknown request type ${storeAction.request}")
                        }
                    } catch (e: Throwable) {
                        storeAction.response.completeExceptionally(e)
                    }
                }
        }
    }
}
