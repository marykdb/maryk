@file:Suppress("EXPERIMENTAL_UNSIGNED_LITERALS", "EXPERIMENTAL_API_USAGE")

package maryk.core.processors.datastore.memory.processors

import maryk.core.models.IsRootValuesDataModel
import maryk.core.processors.datastore.memory.StoreAction
import maryk.core.processors.datastore.memory.records.DataRecord
import maryk.core.properties.PropertyDefinitions
import maryk.core.query.ValuesWithMetaData
import maryk.core.query.requests.GetRequest
import maryk.core.query.responses.ValuesResponse

internal typealias GetStoreAction<DM, P> = StoreAction<DM, P, GetRequest<DM, P>, ValuesResponse<DM, P>>
internal typealias AnyGetStoreAction = GetStoreAction<IsRootValuesDataModel<PropertyDefinitions>, PropertyDefinitions>

internal fun <DM: IsRootValuesDataModel<P>, P: PropertyDefinitions> processGetRequest(storeAction: GetStoreAction<DM, P>, dataList: MutableList<DataRecord<DM, P>>) {
    val getRequest = storeAction.request
    val valuesWithMeta = mutableListOf<ValuesWithMetaData<DM, P>>()

    for (key in getRequest.keys) {
        val index  = dataList.binarySearch { it.key.compareTo(key) }

        // Only return if found
        if (index > -1) {
            val record = dataList[index]

            if (getRequest.filterData(record)) {
                continue
            }

            record.values.toValues(getRequest.dataModel) { values, maxVersion ->
                valuesWithMeta.add(
                    ValuesWithMetaData(
                        key = record.key,
                        values = values,
                        isDeleted = false,
                        firstVersion = record.firstVersion,
                        lastVersion = maxVersion
                    )
                )
            }
        }
    }

    storeAction.response.complete(
        ValuesResponse(
            dataModel = getRequest.dataModel,
            values = valuesWithMeta
        )
    )
}
