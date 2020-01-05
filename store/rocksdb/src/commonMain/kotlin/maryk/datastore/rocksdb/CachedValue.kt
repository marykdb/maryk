package maryk.datastore.rocksdb

/** [value] for cache at [version] */
class CachedValue(
    val version: ULong,
    val value: Any?
)
