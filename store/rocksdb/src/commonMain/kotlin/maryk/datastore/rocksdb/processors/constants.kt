package maryk.datastore.rocksdb.processors

/** Defines last version reference */
internal const val LAST_VERSION_INDICATOR: Byte = 0b1000
internal const val SOFT_DELETE_INDICATOR = 0.toByte()

internal val FALSE = byteArrayOf(0)
internal val TRUE = byteArrayOf(1)

internal val SIMPLE_TYPE_INDICATOR: Byte = 1
internal val COMPLEX_TYPE_INDICATOR: Byte = 2
