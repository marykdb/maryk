package maryk.datastore.shared

/**
 * Calculates index value membership changes without comparing every old value to every new value.
 */
fun diffIndexValues(oldValues: List<ByteArray>, newValues: List<ByteArray>): IndexValueDiff {
    val oldValueKeys = oldValues.mapTo(HashSet(oldValues.size), ::IndexValueKey)
    val newValueKeys = newValues.mapTo(HashSet(newValues.size), ::IndexValueKey)

    return IndexValueDiff(
        removed = oldValues.filter { IndexValueKey(it) !in newValueKeys },
        added = newValues.filter { IndexValueKey(it) !in oldValueKeys },
    )
}

data class IndexValueDiff(
    val removed: List<ByteArray>,
    val added: List<ByteArray>,
)

private class IndexValueKey(private val bytes: ByteArray) {
    override fun equals(other: Any?) = other is IndexValueKey && bytes.contentEquals(other.bytes)

    override fun hashCode() = bytes.contentHashCode()
}
