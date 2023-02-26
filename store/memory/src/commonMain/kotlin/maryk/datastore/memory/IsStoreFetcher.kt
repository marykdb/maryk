package maryk.datastore.memory

import maryk.datastore.memory.records.DataStore

internal typealias IsStoreFetcher<DM> = (DM) -> DataStore<DM>
