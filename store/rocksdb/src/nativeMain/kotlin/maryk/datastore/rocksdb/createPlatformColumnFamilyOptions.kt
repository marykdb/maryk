package maryk.datastore.rocksdb

import maryk.rocksdb.ColumnFamilyOptions

internal actual fun createPlatformColumnFamilyOptions(conflictHistoryBytes: Long?): ColumnFamilyOptions =
    // The portable Native bindings do not yet expose this option.
    ColumnFamilyOptions()
