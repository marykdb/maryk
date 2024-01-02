package maryk.datastore.rocksdb.processors

import maryk.datastore.shared.TypeIndicator


/** Defines last version reference */
internal const val LAST_VERSION_INDICATOR: Byte = 0b1000
internal const val SOFT_DELETE_INDICATOR: Byte = 0

internal const val FALSE: Byte = 0
internal val FALSE_ARRAY = byteArrayOf(FALSE)
internal const val TRUE: Byte = 1

internal val EMPTY_ARRAY = byteArrayOf()

internal val DELETED_INDICATOR_ARRAY = byteArrayOf(TypeIndicator.DeletedIndicator.byte)
