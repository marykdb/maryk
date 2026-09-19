package maryk.datastore.rocksdb.processors.helpers

import maryk.datastore.shared.TypeIndicator

internal val HISTORIC_DELETE_MARKER = byteArrayOf()

internal fun ByteArray.isHistoricDeleteMarker(allowLegacyMarker: Boolean = true) =
    isEmpty() || (
        allowLegacyMarker &&
            size == 1 &&
            this[0] == TypeIndicator.DeletedIndicator.byte
        )
