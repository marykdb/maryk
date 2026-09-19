package maryk.datastore.rocksdb.processors.helpers

import maryk.core.exceptions.StorageException
import maryk.core.properties.types.Key
import maryk.datastore.rocksdb.DBIterator
import maryk.lib.extensions.compare.compareTo

/** Check existence of the [key] on [iterator] by checking existence of creation time */
internal fun checkExistence(
    iterator: DBIterator,
    key: Key<*>
) {
    if (iterator.isValid()) {
        var currentKey = iterator.key()
        when {
            key.bytes.contentEquals(currentKey) -> return
            key.bytes compareTo currentKey < 0 -> {
                iterator.seek(key.bytes)
                if (iterator.isValid() && key.bytes.contentEquals(iterator.key())) {
                    return
                }
                throw StorageException("Key does not exist while it should have existed")
            }
            else -> {
                var exhausted = false
                repeat(MAX_LINEAR_ADVANCE_STEPS) {
                    iterator.next()
                    if (!iterator.isValid()) {
                        exhausted = true
                        return@repeat
                    }
                    currentKey = iterator.key()
                    when {
                        key.bytes.contentEquals(currentKey) -> return
                        key.bytes compareTo currentKey < 0 ->
                            throw StorageException("Key does not exist while it should have existed")
                    }
                }
                if (exhausted) {
                    throw StorageException("Key does not exist while it should have existed")
                }
            }
        }
    }

    iterator.seek(key.bytes)
    if (!iterator.isValid() || !key.bytes.contentEquals(iterator.key())) {
        throw StorageException("Key does not exist while it should have existed")
    }
}

private const val MAX_LINEAR_ADVANCE_STEPS = 8
