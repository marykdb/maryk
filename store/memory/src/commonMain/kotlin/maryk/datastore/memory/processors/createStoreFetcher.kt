package maryk.datastore.memory.processors

import maryk.core.properties.IsRootModel
import maryk.core.properties.types.Key
import maryk.datastore.memory.IsStoreFetcher

internal fun createStoreRecordFetcher(dataStoreFetcher: IsStoreFetcher<*>) =
    { model: IsRootModel, keyToFetch: Key<*> ->
        @Suppress("UNCHECKED_CAST")
        val dataStore = (dataStoreFetcher as IsStoreFetcher<IsRootModel>)(model)
        val index = dataStore.records.binarySearch { it.key compareTo keyToFetch }
        dataStore.records.getOrNull(index)
    }
