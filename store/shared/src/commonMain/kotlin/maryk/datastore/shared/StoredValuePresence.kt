package maryk.datastore.shared

import maryk.core.exceptions.StorageException
import maryk.core.extensions.bytes.initIntByVar
import maryk.core.extensions.bytes.toVarBytes
import maryk.core.properties.definitions.IsCollectionDefinition
import maryk.core.properties.definitions.IsMapDefinition
import maryk.core.properties.definitions.IsPropertyDefinition
import maryk.lib.exceptions.ParseException

/** Two zero bytes cannot be a canonical count or an ordinary tagged value. */
val StoredPropertyDeleteMarker: ByteArray get() = byteArrayOf(0, 0)

fun isStoredPropertyDeleteMarker(bytes: ByteArray, offset: Int = 0, length: Int = bytes.size - offset): Boolean =
    length == 2 && bytes[offset] == 0.toByte() && bytes[offset + 1] == 0.toByte()

/** Read an untagged container count without confusing an empty container with deletion. */
fun readStoredCount(bytes: ByteArray, offset: Int = 0, length: Int = bytes.size - offset): Int? {
    if (length == 0 || isStoredPropertyDeleteMarker(bytes, offset, length)) return null
    var readIndex = offset
    val count = try {
        initIntByVar {
            if (readIndex >= offset + length) throw ParseException("Truncated stored container count")
            bytes[readIndex++]
        }
    } catch (cause: ParseException) {
        throw StorageException("Invalid stored container count: ${cause.message}")
    } catch (cause: IndexOutOfBoundsException) {
        throw StorageException("Invalid stored container count: ${cause.message}")
    }
    if (count < 0 || !count.toVarBytes().contentEquals(bytes.copyOfRange(offset, offset + length))) {
        throw StorageException("Invalid stored container count")
    }
    return count
}

/** Collection counts are untagged, so a zero count is a present empty value. */
fun hasStoredValuePayload(definition: IsPropertyDefinition<*>, bytes: ByteArray, offset: Int, length: Int): Boolean =
    length > 0 && !isStoredPropertyDeleteMarker(bytes, offset, length) &&
        (definition is IsCollectionDefinition<*, *, *, *> || definition is IsMapDefinition<*, *, *> ||
        length != 1 || bytes[offset] != TypeIndicator.DeletedIndicator.byte)
