package maryk.datastore.rocksdb.processors

import kotlin.native.concurrent.SharedImmutable

/** Defines last version reference */
internal const val LAST_VERSION_INDICATOR: Byte = 0b1000
internal const val SOFT_DELETE_INDICATOR: Byte = 0

internal const val FALSE: Byte = 0
@SharedImmutable
internal val FALSE_ARRAY = byteArrayOf(FALSE)
internal const val TRUE: Byte = 1

@SharedImmutable
internal val EMPTY_ARRAY = byteArrayOf()

internal const val DELETED_INDICATOR: Byte = 0
internal const val NO_TYPE_INDICATOR: Byte = 1
internal const val SIMPLE_TYPE_INDICATOR: Byte = 2
internal const val COMPLEX_TYPE_INDICATOR: Byte = 3
internal const val EMBED_INDICATOR: Byte = 4

@SharedImmutable
internal val DELETED_INDICATOR_ARRAY = byteArrayOf(DELETED_INDICATOR)
