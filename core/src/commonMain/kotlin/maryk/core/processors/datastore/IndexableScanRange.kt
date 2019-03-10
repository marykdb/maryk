package maryk.core.processors.datastore

import maryk.lib.extensions.compare.compareDefinedTo
import maryk.lib.extensions.compare.compareTo

/**
 * Defines a range to scan on Indexables. Also contains partial matches to check.
 */
class IndexableScanRange internal constructor(
    start: ByteArray,
    end: ByteArray? = null,
    partialMatches: List<IsIndexPartialToMatch>? = null
): ScanRange(start, end, partialMatches) {
    override fun keyBeforeStart(key: ByteArray) =  key < start
    override fun keyOutOfRange(key: ByteArray) = end?.let {  end.compareDefinedTo(key) < 0 } ?: false
}
