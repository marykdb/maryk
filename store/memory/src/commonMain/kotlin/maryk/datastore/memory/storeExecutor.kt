package maryk.datastore.memory

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
import maryk.datastore.memory.records.AnyDataStore

/** Executor of StoreActions onto DataStore */
@Suppress("UNCHECKED_CAST")
internal val storeExecutor: StoreExecutor<*, *> = { storeAction, dataStore ->
    when(storeAction.request) {
        is AddRequest<*, *> ->
            processAddRequest(storeAction as AnyAddStoreAction, dataStore as AnyDataStore)
        is GetRequest<*, *> ->
            processGetRequest(storeAction as AnyGetStoreAction, dataStore as AnyDataStore)
        is GetChangesRequest<*, *> ->
            processGetChangesRequest(storeAction as AnyGetChangesStoreAction, dataStore as AnyDataStore)
        is ChangeRequest<*> ->
            processChangeRequest(storeAction as AnyChangeStoreAction, dataStore as AnyDataStore)
        is DeleteRequest<*> ->
            processDeleteRequest(storeAction as AnyDeleteStoreAction, dataStore as AnyDataStore)
        is ScanRequest<*, *> ->
            processScanRequest(storeAction as AnyScanStoreAction, dataStore as AnyDataStore)
        is ScanChangesRequest<*, *> ->
            processScanChangesRequest(storeAction as AnyScanChangesStoreAction, dataStore as AnyDataStore)
        else -> throw Exception("Unknown request type ${storeAction.request}")
    }
}
