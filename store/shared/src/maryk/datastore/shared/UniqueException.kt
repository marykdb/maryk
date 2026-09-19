package maryk.datastore.shared

import maryk.core.properties.types.Key

/**
 * Exception thrown when unique value already exists in index
 * with [reference] to which unique index and [key] of existing object
 */
class UniqueException(
    val reference: ByteArray,
    val key: Key<*>
) : Error()
