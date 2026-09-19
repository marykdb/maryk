package maryk.datastore.memory.processors

import maryk.core.clock.HLC
import maryk.core.models.IsRootDataModel
import maryk.core.properties.types.Key
import maryk.datastore.memory.IsStoreFetcher

internal fun createStoreRecordFetcher(
    dataStoreFetcher: IsStoreFetcher<*>,
    toVersion: HLC? = null
) =
    { model: IsRootDataModel, keyToFetch: Key<*> ->
        @Suppress("UNCHECKED_CAST")
        val dataStore = (dataStoreFetcher as IsStoreFetcher<IsRootDataModel>)(model)
        dataStore.getByKeyAtVersion(keyToFetch.bytes, toVersion)
    }
