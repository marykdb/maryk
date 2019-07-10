package maryk.datastore.rocksdb.processors.helpers

import maryk.core.exceptions.StorageException
import maryk.core.extensions.bytes.toULong
import maryk.core.models.IsRootDataModel
import maryk.core.properties.types.Key
import maryk.datastore.rocksdb.TableColumnFamilies
import maryk.datastore.rocksdb.processors.LAST_VERSION_INDICATOR
import maryk.rocksdb.ReadOptions
import maryk.rocksdb.Transaction

/**
 * Get last version for given key to compare with. Object should exist or it throws an exception
 */
fun <DM: IsRootDataModel<*>> getLastVersion(transaction: Transaction, columnFamilies: TableColumnFamilies, readOptions: ReadOptions, key: Key<DM>) =
    transaction.get(columnFamilies.table, readOptions, byteArrayOf(*key.bytes,
        LAST_VERSION_INDICATOR
    ))?.toULong() ?: throw StorageException("Can only retrieve last versions of existing objects")
