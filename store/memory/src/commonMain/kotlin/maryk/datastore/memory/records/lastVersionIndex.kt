package maryk.datastore.memory.records

import maryk.core.clock.HLC

/** Histories are appended in version order; equal versions retain their last-write ordering. */
internal inline fun <T> List<T>.lastVersionIndex(toVersion: HLC, version: (T) -> HLC): Int {
    if (isEmpty()) return -1
    if (version(last()) <= toVersion) return lastIndex
    var low = 0
    var high = lastIndex
    while (low < high) {
        val middle = low + (high - low) / 2
        if (version(this[middle]) <= toVersion) low = middle + 1 else high = middle
    }
    return low - 1
}
