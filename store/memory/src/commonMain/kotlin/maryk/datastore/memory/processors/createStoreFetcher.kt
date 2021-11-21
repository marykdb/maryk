package maryk.datastore.memory.processors

import maryk.core.models.IsRootValuesDataModel
import maryk.core.properties.types.Key
import maryk.datastore.memory.IsStoreFetcher

internal fun createStoreRecordFetcher(dataStoreFetcher: IsStoreFetcher<*, *>) =
    { model: IsRootValuesDataModel<*>, keyToFetch: Key<*> ->
        val dataStore = dataStoreFetcher(model)
        val index = dataStore.records.binarySearch { it.key compareTo keyToFetch }
        dataStore.records.getOrNull(index)
    }
