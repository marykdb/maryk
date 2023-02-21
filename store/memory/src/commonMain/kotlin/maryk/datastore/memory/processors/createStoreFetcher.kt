package maryk.datastore.memory.processors

import maryk.core.models.IsRootDataModel
import maryk.core.properties.types.Key
import maryk.datastore.memory.IsStoreFetcher

internal fun createStoreRecordFetcher(dataStoreFetcher: IsStoreFetcher<*, *>) =
    { model: IsRootDataModel<*>, keyToFetch: Key<*> ->
        val dataStore = dataStoreFetcher(model)
        val index = dataStore.records.binarySearch { it.key compareTo keyToFetch }
        dataStore.records.getOrNull(index)
    }
