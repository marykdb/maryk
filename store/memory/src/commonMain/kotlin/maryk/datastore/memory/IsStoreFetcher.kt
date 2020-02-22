package maryk.datastore.memory

import maryk.core.models.IsRootValuesDataModel
import maryk.datastore.memory.records.DataStore

internal typealias IsStoreFetcher<DM, P> = (IsRootValuesDataModel<DM>) -> DataStore<DM, P>
