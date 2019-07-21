package maryk.datastore.rocksdb.processors.helpers

import maryk.core.extensions.bytes.toULong
import maryk.datastore.rocksdb.TableColumnFamilies
import maryk.rocksdb.ReadOptions
import maryk.rocksdb.Transaction

internal fun readCreationVersion(
    transaction: Transaction,
    columnFamilies: TableColumnFamilies,
    readOptions: ReadOptions,
    key: ByteArray
) = transaction.get(columnFamilies.table, readOptions, key)?.toULong()
