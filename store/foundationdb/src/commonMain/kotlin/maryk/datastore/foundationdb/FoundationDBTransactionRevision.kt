package maryk.datastore.foundationdb

import maryk.core.properties.types.Bytes
import maryk.datastore.foundationdb.processors.helpers.awaitResult
import maryk.foundationdb.Transaction

internal fun Transaction.readTransactionRevision(key: ByteArray, requireConflict: Boolean): Bytes {
    val reader = if (requireConflict) this else snapshot()
    return Bytes(reader.get(key).awaitResult() ?: byteArrayOf(0))
}
