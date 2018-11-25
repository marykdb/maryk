package maryk.datastore.memory

import maryk.core.models.IsRootValuesDataModel
import maryk.datastore.memory.processors.AnyAddStoreAction
import maryk.datastore.memory.processors.AnyChangeStoreAction
import maryk.datastore.memory.processors.AnyDeleteStoreAction
import maryk.datastore.memory.processors.AnyGetStoreAction
import maryk.datastore.memory.processors.AnyScanStoreAction
import maryk.datastore.memory.processors.processAddRequest
import maryk.datastore.memory.processors.processChangeRequest
import maryk.datastore.memory.processors.processDeleteRequest
import maryk.datastore.memory.processors.processGetRequest
import maryk.datastore.memory.processors.processScanRequest
import maryk.datastore.memory.records.DataRecord
import maryk.core.properties.PropertyDefinitions
import maryk.core.query.requests.AddRequest
import maryk.core.query.requests.ChangeRequest
import maryk.core.query.requests.DeleteRequest
import maryk.core.query.requests.GetRequest
import maryk.core.query.requests.ScanRequest

internal typealias AnyDataList = MutableList<DataRecord<IsRootValuesDataModel<PropertyDefinitions>, PropertyDefinitions>>

@Suppress("UNCHECKED_CAST")
internal val storeExecutor: StoreExecutor<*, *> = { store, storeAction, dataList ->
    when(storeAction.request) {
        is AddRequest<*, *> ->
            processAddRequest(storeAction as AnyAddStoreAction, dataList)
        is GetRequest<*, *> ->
            processGetRequest(storeAction as AnyGetStoreAction, dataList as AnyDataList)
        is ChangeRequest<*> ->
            store.processChangeRequest(storeAction as AnyChangeStoreAction, dataList as AnyDataList)
        is DeleteRequest<*> ->
            processDeleteRequest(storeAction as AnyDeleteStoreAction, dataList as AnyDataList)
        is ScanRequest<*, *> ->
            processScanRequest(storeAction as AnyScanStoreAction, dataList as AnyDataList)
        else -> throw Exception("Unknown request type ${storeAction.request}")
    }
}
