package maryk.datastore.indexeddb

import maryk.datastore.indexeddb.records.DataStore

internal typealias IsStoreFetcher<DM> = (DM) -> DataStore<DM>
