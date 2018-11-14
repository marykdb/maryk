package maryk.core.processors.datastore.memory

import maryk.core.models.IsRootValuesDataModel
import maryk.core.processors.datastore.memory.processors.AnyAddStoreAction
import maryk.core.processors.datastore.memory.processors.AnyChangeStoreAction
import maryk.core.processors.datastore.memory.processors.AnyDeleteStoreAction
import maryk.core.processors.datastore.memory.processors.AnyGetStoreAction
import maryk.core.processors.datastore.memory.processors.AnyScanStoreAction
import maryk.core.processors.datastore.memory.processors.processAddRequest
import maryk.core.processors.datastore.memory.processors.processChangeRequest
import maryk.core.processors.datastore.memory.processors.processDeleteRequest
import maryk.core.processors.datastore.memory.processors.processGetRequest
import maryk.core.processors.datastore.memory.processors.processScanRequest
import maryk.core.processors.datastore.memory.records.DataRecord
import maryk.core.properties.PropertyDefinitions
import maryk.core.query.requests.AddRequest
import maryk.core.query.requests.ChangeRequest
import maryk.core.query.requests.DeleteRequest
import maryk.core.query.requests.GetRequest
import maryk.core.query.requests.ScanRequest

internal typealias AnyDataList = MutableList<DataRecord<IsRootValuesDataModel<PropertyDefinitions>, PropertyDefinitions>>

@Suppress("UNCHECKED_CAST")
internal val storeExecutor: StoreExecutor<*, *> = { _, storeAction, dataList ->
    when(storeAction.request) {
        is AddRequest<*, *> ->
            processAddRequest(storeAction as AnyAddStoreAction, dataList)
        is GetRequest<*, *> ->
            processGetRequest(storeAction as AnyGetStoreAction, dataList as AnyDataList)
        is ChangeRequest<*> ->
            processChangeRequest(storeAction as AnyChangeStoreAction, dataList as AnyDataList)
        is DeleteRequest<*> ->
            processDeleteRequest(storeAction as AnyDeleteStoreAction, dataList as AnyDataList)
        is ScanRequest<*, *> ->
            processScanRequest(storeAction as AnyScanStoreAction, dataList as AnyDataList)
        else -> throw Exception("Unknown request type ${storeAction.request}")
    }
}
