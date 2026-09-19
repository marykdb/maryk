package maryk.datastore.foundationdb.processors.helpers

import maryk.datastore.foundationdb.processors.HISTORIC_DELETE_MARKER

internal fun ByteArray.isHistoricDeleteMarker() =
    this.isEmpty() || this.contentEquals(HISTORIC_DELETE_MARKER)
