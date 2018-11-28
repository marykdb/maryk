package maryk.datastore.memory.records

/** Exception thrown when unique value already exists in index */
class UniqueException(val reference: ByteArray): Throwable()