package maryk.datastore.rocksdb

import maryk.rocksdb.ColumnFamilyOptions
import java.util.Properties

internal actual fun createPlatformColumnFamilyOptions(conflictHistoryBytes: Long?): ColumnFamilyOptions =
    if (conflictHistoryBytes == null) ColumnFamilyOptions()
    else ColumnFamilyOptions.getColumnFamilyOptionsFromProps(Properties().apply {
        setProperty("max_write_buffer_size_to_maintain", conflictHistoryBytes.toString())
    }) ?: ColumnFamilyOptions()
