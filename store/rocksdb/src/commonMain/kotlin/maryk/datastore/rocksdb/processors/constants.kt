package maryk.datastore.rocksdb.processors

/** Defines last version reference */
internal const val LAST_VERSION_INDICATOR: Byte = 0b1000
internal const val SOFT_DELETE_INDICATOR: Byte = 0

internal const val FALSE: Byte = 0
internal val FALSE_ARRAY = byteArrayOf(FALSE)
internal const val TRUE: Byte = 1
internal val TRUE_ARRAY = byteArrayOf(TRUE)

internal const val NO_TYPE_INDICATOR: Byte = 0
internal const val SIMPLE_TYPE_INDICATOR: Byte = 1
internal const val COMPLEX_TYPE_INDICATOR: Byte = 2
internal const val EMBED_INDICATOR: Byte = 3
