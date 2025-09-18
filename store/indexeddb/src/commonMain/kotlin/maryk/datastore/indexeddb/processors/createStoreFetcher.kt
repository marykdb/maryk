package maryk.datastore.indexeddb.processors

import maryk.core.models.IsRootDataModel
import maryk.core.properties.types.Key
import maryk.datastore.indexeddb.IsStoreFetcher

internal fun createStoreRecordFetcher(dataStoreFetcher: IsStoreFetcher<*>) =
    { model: IsRootDataModel, keyToFetch: Key<*> ->
        @Suppress("UNCHECKED_CAST")
        val dataStore = (dataStoreFetcher as IsStoreFetcher<IsRootDataModel>)(model)
        val index = dataStore.records.binarySearch { it.key compareTo keyToFetch }
        dataStore.records.getOrNull(index)
    }
