package maryk.datastore.rocksdb.processors.helpers

import maryk.core.extensions.bytes.initULong
import maryk.datastore.rocksdb.TableColumnFamilies
import maryk.rocksdb.ReadOptions
import maryk.rocksdb.Transaction

/**
 * Get a unique record key by value
 */
fun getKeyByUniqueValue(
    transaction: Transaction,
    columnFamilies: TableColumnFamilies,
    readOptions: ReadOptions,
    reference: ByteArray,
    processKey: (() -> Byte, ULong) -> Unit
) {
    transaction.get(columnFamilies.unique, readOptions, reference)?.let { versionAndKey ->
        var readIndex = 0
        val versionAndKeyReader = { versionAndKey[readIndex++] }
        val setAtVersion = initULong(versionAndKeyReader)
        processKey(versionAndKeyReader, setAtVersion)
    }
}
