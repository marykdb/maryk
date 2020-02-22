package maryk.datastore.memory

import maryk.core.exceptions.TypeException
import maryk.core.query.requests.AddRequest
import maryk.core.query.requests.ChangeRequest
import maryk.core.query.requests.DeleteRequest
import maryk.core.query.requests.GetChangesRequest
import maryk.core.query.requests.GetRequest
import maryk.core.query.requests.ScanChangesRequest
import maryk.core.query.requests.ScanRequest
import maryk.datastore.memory.processors.AnyAddStoreAction
import maryk.datastore.memory.processors.AnyChangeStoreAction
import maryk.datastore.memory.processors.AnyDeleteStoreAction
import maryk.datastore.memory.processors.AnyGetChangesStoreAction
import maryk.datastore.memory.processors.AnyGetStoreAction
import maryk.datastore.memory.processors.AnyScanChangesStoreAction
import maryk.datastore.memory.processors.AnyScanStoreAction
import maryk.datastore.memory.processors.processAddRequest
import maryk.datastore.memory.processors.processChangeRequest
import maryk.datastore.memory.processors.processDeleteRequest
import maryk.datastore.memory.processors.processGetChangesRequest
import maryk.datastore.memory.processors.processGetRequest
import maryk.datastore.memory.processors.processScanChangesRequest
import maryk.datastore.memory.processors.processScanRequest

/** Executor of StoreActions onto DataStore */
@Suppress("UNCHECKED_CAST")
internal val storeExecutor: StoreExecutor<*, *> = { storeAction, dataStoreFetcher ->
    when (storeAction.request) {
        is AddRequest<*, *> ->
            processAddRequest(storeAction as AnyAddStoreAction, dataStoreFetcher)
        is GetRequest<*, *> ->
            processGetRequest(storeAction as AnyGetStoreAction, dataStoreFetcher)
        is GetChangesRequest<*, *> ->
            processGetChangesRequest(storeAction as AnyGetChangesStoreAction, dataStoreFetcher)
        is ChangeRequest<*> ->
            processChangeRequest(storeAction as AnyChangeStoreAction, dataStoreFetcher)
        is DeleteRequest<*> ->
            processDeleteRequest(storeAction as AnyDeleteStoreAction, dataStoreFetcher)
        is ScanRequest<*, *> ->
            processScanRequest(storeAction as AnyScanStoreAction, dataStoreFetcher)
        is ScanChangesRequest<*, *> ->
            processScanChangesRequest(storeAction as AnyScanChangesStoreAction, dataStoreFetcher)
        else -> throw TypeException("Unknown request type ${storeAction.request}")
    }
}
