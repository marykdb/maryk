package maryk.datastore.rocksdb.processors

/** Defines last version reference */
internal const val LAST_VERSION_INDICATOR: Byte = 0b1000
internal const val SOFT_DELETE_INDICATOR: Byte = 0

internal const val FALSE: Byte = 0
internal val FALSE_ARRAY = byteArrayOf(FALSE)
internal const val TRUE: Byte = 1

internal val EMPTY_ARRAY = byteArrayOf()

internal const val DELETED_INDICATOR: Byte = 0
internal const val NO_TYPE_INDICATOR: Byte = 1
internal const val SIMPLE_TYPE_INDICATOR: Byte = 2
internal const val COMPLEX_TYPE_INDICATOR: Byte = 3
internal const val EMBED_INDICATOR: Byte = 4

internal val DELETED_INDICATOR_ARRAY = byteArrayOf(DELETED_INDICATOR)
