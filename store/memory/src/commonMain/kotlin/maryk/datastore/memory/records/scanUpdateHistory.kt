package maryk.datastore.memory.records

import maryk.datastore.memory.records.DataStore.UpdateHistoryRecord
import maryk.lib.extensions.compare.compareTo

/** Read the maintained descending version/key index in descending version, ascending key order. */
internal fun List<UpdateHistoryRecord>.scanUpdateHistory(
    fromVersion: ULong,
    toVersion: ULong,
    afterKey: ByteArray? = null,
): Sequence<UpdateHistoryRecord> = sequence {
    var low = 0
    var high = size
    while (low < high) {
        val middle = low + (high - low) / 2
        if (this@scanUpdateHistory[middle].version > toVersion) low = middle + 1 else high = middle
    }
    var groupStart = low
    while (groupStart < size) {
        val version = this@scanUpdateHistory[groupStart].version
        if (version < fromVersion) break
        low = groupStart + 1
        high = if (low < size && this@scanUpdateHistory[low].version == version) size else low
        while (low < high) {
            val middle = low + (high - low) / 2
            if (this@scanUpdateHistory[middle].version == version) low = middle + 1 else high = middle
        }
        val groupEnd = low
        var keyEnd = groupEnd
        if (version == toVersion && afterKey != null) {
            low = groupStart
            high = groupEnd
            while (low < high) {
                val middle = low + (high - low) / 2
                if (this@scanUpdateHistory[middle].keyBytes > afterKey) low = middle + 1 else high = middle
            }
            keyEnd = low
        }
        while (keyEnd > groupStart) {
            val key = this@scanUpdateHistory[keyEnd - 1].keyBytes
            var keyStart = keyEnd - 1
            while (keyStart > groupStart && this@scanUpdateHistory[keyStart - 1].keyBytes.contentEquals(key)) keyStart--
            // Preserve the stable order of entries sharing both a key and a version.
            for (index in keyStart until keyEnd) yield(this@scanUpdateHistory[index])
            keyEnd = keyStart
        }
        groupStart = groupEnd
    }
}
