package maryk.datastore.foundationdb.processors.helpers

import maryk.core.properties.references.AnyPropertyReference
import maryk.datastore.foundationdb.IsTableDirectories
import maryk.datastore.shared.hasActiveStoredType
import maryk.datastore.shared.storedTypeIndex
import maryk.foundationdb.Transaction

internal fun Transaction.hasActiveStoredType(
    directories: IsTableDirectories,
    key: ByteArray,
    reference: AnyPropertyReference,
    toVersion: ULong?,
    decryptValue: DecryptValue? = null,
): Boolean = hasActiveStoredType(reference) { parent ->
    getValue(directories, toVersion, key + parent.toStorageByteArray(), keyLength = key.size, decryptValue = decryptValue) { bytes, offset, length ->
        storedTypeIndex(bytes, offset, length)
    }
}
