package maryk.datastore.shared

/** [value] for cache at [version] */
class CachedValue(
    val version: ULong,
    val value: Any?
)
