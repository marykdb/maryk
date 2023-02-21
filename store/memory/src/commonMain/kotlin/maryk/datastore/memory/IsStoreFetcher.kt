package maryk.datastore.memory

import maryk.core.models.IsRootDataModel
import maryk.datastore.memory.records.DataStore

internal typealias IsStoreFetcher<DM, P> = (IsRootDataModel<DM>) -> DataStore<DM, P>
