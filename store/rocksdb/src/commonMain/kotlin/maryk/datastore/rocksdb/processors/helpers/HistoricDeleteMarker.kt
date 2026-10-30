package maryk.datastore.rocksdb.processors.helpers

import maryk.datastore.shared.TypeIndicator
import maryk.datastore.shared.isStoredPropertyDeleteMarker

internal val HISTORIC_DELETE_MARKER = byteArrayOf()

internal fun ByteArray.isHistoricDeleteMarker(allowLegacyMarker: Boolean = false) =
    isEmpty() || isStoredPropertyDeleteMarker(this) || (
        allowLegacyMarker &&
            size == 1 &&
            this[0] == TypeIndicator.DeletedIndicator.byte
        )
