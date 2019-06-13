package maryk.datastore.rocksdb

import maryk.core.exceptions.TypeException
import maryk.core.query.requests.AddRequest
import maryk.core.query.requests.ChangeRequest
import maryk.core.query.requests.DeleteRequest
import maryk.core.query.requests.GetChangesRequest
import maryk.core.query.requests.GetRequest
import maryk.core.query.requests.ScanChangesRequest
import maryk.core.query.requests.ScanRequest
import maryk.datastore.rocksdb.processors.AnyAddStoreAction
import maryk.datastore.rocksdb.processors.processAddRequest

/** Executor of StoreActions onto DataStore */
@Suppress("UNCHECKED_CAST")
internal val storeExecutor: StoreExecutor = { storeAction, db ->
    when (storeAction.request) {
        is AddRequest<*, *> ->
            processAddRequest(storeAction as AnyAddStoreAction, db)
        is GetRequest<*, *> ->
            TODO("GET")
        is GetChangesRequest<*, *> ->
            TODO("GET CHANGES")
        is ChangeRequest<*> ->
            TODO("CHANGE")
        is DeleteRequest<*> ->
            TODO("DELETE")
        is ScanRequest<*, *> ->
            TODO("SCAN")
        is ScanChangesRequest<*, *> ->
            TODO("SCAN CHANGES")
        else -> throw TypeException("Unknown request type ${storeAction.request}")
    }
}
