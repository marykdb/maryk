package maryk.datastore.rocksdb

import maryk.rocksdb.ColumnFamilyOptions

internal expect fun createPlatformColumnFamilyOptions(conflictHistoryBytes: Long?): ColumnFamilyOptions
