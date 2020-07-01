package maryk.datastore.rocksdb

import maryk.core.exceptions.TypeException
import maryk.core.query.requests.AddRequest
import maryk.core.query.requests.ChangeRequest
import maryk.core.query.requests.DeleteRequest
import maryk.core.query.requests.GetChangesRequest
import maryk.core.query.requests.GetRequest
import maryk.core.query.requests.GetUpdatesRequest
import maryk.core.query.requests.ScanChangesRequest
import maryk.core.query.requests.ScanRequest
import maryk.core.query.requests.ScanUpdatesRequest
import maryk.datastore.rocksdb.processors.AnyAddStoreAction
import maryk.datastore.rocksdb.processors.AnyChangeStoreAction
import maryk.datastore.rocksdb.processors.AnyDeleteStoreAction
import maryk.datastore.rocksdb.processors.AnyGetChangesStoreAction
import maryk.datastore.rocksdb.processors.AnyGetStoreAction
import maryk.datastore.rocksdb.processors.AnyGetUpdatesStoreAction
import maryk.datastore.rocksdb.processors.AnyScanChangesStoreAction
import maryk.datastore.rocksdb.processors.AnyScanStoreAction
import maryk.datastore.rocksdb.processors.AnyScanUpdatesStoreAction
import maryk.datastore.rocksdb.processors.processAddRequest
import maryk.datastore.rocksdb.processors.processChangeRequest
import maryk.datastore.rocksdb.processors.processDeleteRequest
import maryk.datastore.rocksdb.processors.processGetChangesRequest
import maryk.datastore.rocksdb.processors.processGetRequest
import maryk.datastore.rocksdb.processors.processGetUpdatesRequest
import maryk.datastore.rocksdb.processors.processScanChangesRequest
import maryk.datastore.rocksdb.processors.processScanRequest
import maryk.datastore.rocksdb.processors.processScanUpdatesRequest
import kotlin.native.concurrent.SharedImmutable

/** Executor of StoreActions onto DataStore */
@Suppress("UNCHECKED_CAST")
@SharedImmutable
internal val storeExecutor: StoreExecutor = { storeAction, db, updateSendChannel ->
    when (storeAction.request) {
        is AddRequest<*, *> ->
            processAddRequest(storeAction as AnyAddStoreAction, db, updateSendChannel)
        is ChangeRequest<*> ->
            processChangeRequest(storeAction as AnyChangeStoreAction, db, updateSendChannel)
        is DeleteRequest<*> ->
            processDeleteRequest(storeAction as AnyDeleteStoreAction, db, updateSendChannel)
        is GetRequest<*, *> ->
            processGetRequest(storeAction as AnyGetStoreAction, db)
        is GetChangesRequest<*, *> ->
            processGetChangesRequest(storeAction as AnyGetChangesStoreAction, db)
        is GetUpdatesRequest<*, *> ->
            processGetUpdatesRequest(storeAction as AnyGetUpdatesStoreAction, db)
        is ScanRequest<*, *> ->
            processScanRequest(storeAction as AnyScanStoreAction, db)
        is ScanChangesRequest<*, *> ->
            processScanChangesRequest(storeAction as AnyScanChangesStoreAction, db)
        is ScanUpdatesRequest<*, *> ->
            processScanUpdatesRequest(storeAction as AnyScanUpdatesStoreAction, db)
        else -> throw TypeException("Unknown request type ${storeAction.request}")
    }
}
