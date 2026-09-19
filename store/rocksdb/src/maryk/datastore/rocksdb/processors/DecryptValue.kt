package maryk.datastore.rocksdb.processors

internal typealias DecryptValue = (ByteArray, ByteArray, ByteArray, Int, Int) -> ByteArray
