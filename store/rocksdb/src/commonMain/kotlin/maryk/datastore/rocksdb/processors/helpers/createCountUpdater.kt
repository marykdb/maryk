package maryk.datastore.rocksdb.processors.helpers

import maryk.core.extensions.bytes.initIntByVar
import maryk.core.extensions.bytes.toVarBytes
import maryk.core.properties.references.TypedPropertyReference
import maryk.core.properties.types.Key
import maryk.datastore.rocksdb.TableColumnFamilies
import maryk.rocksdb.ReadOptions
import maryk.rocksdb.Transaction

/**
 * Set count for [reference] at [version] by applying [countChange] to current count read with [readOptions]
 */
internal fun <T : Any> createCountUpdater(
    transaction: Transaction,
    columnFamilies: TableColumnFamilies,
    readOptions: ReadOptions,
    key: Key<*>,
    reference: TypedPropertyReference<out T>,
    versionBytes: ByteArray,
    countChange: Int,
    sizeValidator: (UInt) -> Unit
) {
    val referenceToCompareTo = reference.toStorageByteArray()

    val previousCount = transaction.getValue(columnFamilies, readOptions, null, byteArrayOf(*key.bytes, *referenceToCompareTo)) { b, o, _ ->
        var readIndex = o
        initIntByVar { b[readIndex++] }
    } ?: 0

    val newCount = maxOf(0, previousCount + countChange)

    sizeValidator(newCount.toUInt())

    setValue(transaction, columnFamilies, key, referenceToCompareTo, versionBytes, newCount.toVarBytes())
}
