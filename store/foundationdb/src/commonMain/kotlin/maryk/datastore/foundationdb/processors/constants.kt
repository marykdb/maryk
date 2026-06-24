package maryk.datastore.foundationdb.processors

import maryk.datastore.shared.TypeIndicator

internal const val SOFT_DELETE_INDICATOR: Byte = 0

internal const val TRUE: Byte = 1

internal val EMPTY_BYTEARRAY = byteArrayOf()
internal val HISTORIC_REMOVAL_MARKER = byteArrayOf(TRUE)
internal val HISTORIC_DELETE_MARKER = TypeIndicator.DeletedIndicator.byteArray
