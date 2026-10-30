package maryk.datastore.shared

import maryk.core.exceptions.StorageException
import maryk.core.extensions.bytes.initIntByVar
import maryk.lib.exceptions.ParseException

/** Strip anonymous collection qualifiers and descendants before incrementing a map key. */
fun incrementingMapKeyPrefix(qualifier: ByteArray, reference: ByteArray, keyByteSize: Int): ByteArray {
    if (qualifier.size <= reference.size || !qualifier.copyOfRange(0, reference.size).contentEquals(reference)) {
        throw StorageException("Incrementing map qualifier does not contain a key")
    }
    var readIndex = reference.size
    val keyLength = try {
        initIntByVar { qualifier[readIndex++] }
    } catch (cause: ParseException) {
        throw StorageException("Invalid incrementing map key qualifier: ${cause.message}")
    } catch (cause: IndexOutOfBoundsException) {
        throw StorageException("Invalid incrementing map key qualifier: ${cause.message}")
    }
    if (keyLength != keyByteSize || keyLength > qualifier.size - readIndex) {
        throw StorageException("Invalid incrementing map key qualifier")
    }
    return qualifier.copyOfRange(0, readIndex + keyLength)
}
