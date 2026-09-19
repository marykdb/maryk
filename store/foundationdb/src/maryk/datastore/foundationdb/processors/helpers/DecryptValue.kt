package maryk.datastore.foundationdb.processors.helpers

/** Decrypts a stored payload with its logical record and qualifier binding. */
internal typealias DecryptValue = (UInt, ByteArray, Int, Int, ByteArray, ByteArray) -> ByteArray
