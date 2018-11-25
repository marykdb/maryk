@file:Suppress("EXPERIMENTAL_UNSIGNED_LITERALS", "EXPERIMENTAL_API_USAGE")

package maryk.datastore.memory.processors

import maryk.core.models.IsRootValuesDataModel
import maryk.datastore.memory.StoreAction
import maryk.datastore.memory.records.DataRecord
import maryk.core.properties.PropertyDefinitions
import maryk.core.query.ValuesWithMetaData
import maryk.core.query.requests.ScanRequest
import maryk.core.query.responses.ValuesResponse

internal typealias ScanStoreAction<DM, P> = StoreAction<DM, P, ScanRequest<DM, P>, ValuesResponse<DM, P>>
internal typealias AnyScanStoreAction = ScanStoreAction<IsRootValuesDataModel<PropertyDefinitions>, PropertyDefinitions>

internal fun <DM: IsRootValuesDataModel<P>, P: PropertyDefinitions> processScanRequest(storeAction: ScanStoreAction<DM, P>, dataList: MutableList<DataRecord<DM, P>>) {
    val scanRequest = storeAction.request
    val valuesWithMeta = mutableListOf<ValuesWithMetaData<DM, P>>()

    val startIndex = dataList.binarySearch { it.key.compareTo(scanRequest.startKey) }.let {
        // If negative start at first entry point
        if (it < 0) it * -1 + 1 else it
    }

    for (index in startIndex until dataList.size) {
        val record = dataList[index]

        if (scanRequest.filterData(record)) {
            continue
        }

        valuesWithMeta += scanRequest.dataModel.recordToValueWithMeta(record)

        // Break when limit is found
        if (valuesWithMeta.size.toUInt() == scanRequest.limit) break
    }

    storeAction.response.complete(
        ValuesResponse(
            dataModel = scanRequest.dataModel,
            values = valuesWithMeta
        )
    )
}
