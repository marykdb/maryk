package maryk.datastore.hbase.processors

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.future.await
import maryk.core.clock.HLC
import maryk.core.models.IsRootDataModel
import maryk.core.properties.types.Key
import maryk.core.query.responses.statuses.DeleteSuccess
import maryk.core.query.responses.statuses.DoesNotExist
import maryk.core.query.responses.statuses.IsDeleteResponseStatus
import maryk.core.query.responses.statuses.ServerFail
import maryk.datastore.hbase.HbaseDataStore
import maryk.datastore.hbase.MetaColumns
import maryk.datastore.hbase.dataColumnFamily
import maryk.datastore.hbase.metaColumnFamily
import maryk.datastore.hbase.softDeleteIndicator
import maryk.datastore.hbase.trueIndicator
import maryk.datastore.shared.Cache
import maryk.datastore.shared.updates.IsUpdateAction
import maryk.datastore.shared.updates.Update
import org.apache.hadoop.hbase.client.Delete
import org.apache.hadoop.hbase.client.Get
import org.apache.hadoop.hbase.client.Put

internal suspend fun <DM : IsRootDataModel> processDelete(
    dataStore: HbaseDataStore,
    dataModel: DM,
    key: Key<DM>,
    version: HLC,
    dbIndex: UInt,
    hardDelete: Boolean,
    cache: Cache,
    updateSharedFlow: MutableSharedFlow<IsUpdateAction>
): IsDeleteResponseStatus<DM> = try {
    val table = dataStore.getTable(dataModel)

    val exists = table.exists(Get(key.bytes)).await()

    when {
        exists -> {
            // Create version bytes
            val versionBytes = HLC.toStorageBytes(version)

//                for (reference in dataStore.getUniqueIndices(
//                    dbIndex, columnFamilies.unique
//                )) {
//                    val referenceAndKey = byteArrayOf(*key.bytes, *reference)
//                    val valueLength = transaction.get(
//                        columnFamilies.table,
//                        dataStore.defaultReadOptions,
//                        referenceAndKey,
//                        recyclableByteArray
//                    )
//
//                    if (valueLength != RocksDB.NOT_FOUND) {
//                        val value = if (valueLength > recyclableByteArray.size) {
//                            // Large value which did not fit in recyclableByteArray
//                            transaction.get(columnFamilies.table, dataStore.defaultReadOptions, referenceAndKey)!!
//                        } else recyclableByteArray
//
//                        deleteUniqueIndexValue(
//                            transaction,
//                            columnFamilies,
//                            reference,
//                            value,
//                            VERSION_BYTE_SIZE,
//                            valueLength - VERSION_BYTE_SIZE,
//                            versionBytes,
//                            hardDelete
//                        )
//                    }
//                }
//
//                // Delete indexed values
//                dataModel.Meta.indices?.let { indices ->
//                    val valuesGetter = DBAccessorStoreValuesGetter(columnFamilies, dataStore.defaultReadOptions)
//                    valuesGetter.moveToKey(key.bytes, transaction)
//
//                    indices.forEach { indexable ->
//                        val indexReference = indexable.referenceStorageByteArray.bytes
//                        val valueAndKeyBytes = indexable.toStorageByteArrayForIndex(valuesGetter, key.bytes)
//                            ?: return@forEach // skip if no complete values to index are found
//
//                        deleteIndexValue(
//                            transaction,
//                            columnFamilies,
//                            indexReference,
//                            valueAndKeyBytes,
//                            versionBytes,
//                            hardDelete
//                        )
//                    }
//                }

            if (hardDelete) {
                cache.delete(dbIndex, key)

                val delete = Delete(key.bytes).setTimestamp(version.timestamp.toLong())

                table.delete(delete).await()
            } else {
                val put = Put(key.bytes)

                put.addColumn(metaColumnFamily, MetaColumns.LatestVersion.byteArray, version.timestamp.toLong(), versionBytes)
                put.addColumn(dataColumnFamily, softDeleteIndicator, version.timestamp.toLong(), trueIndicator)

                table.put(put).await()
            }

            updateSharedFlow.emit(
                Update.Deletion(dataModel, key, version.timestamp, hardDelete)
            )

            DeleteSuccess(version.timestamp)
        }
        else -> DoesNotExist(key)
    }
} catch (e: Throwable) {
    ServerFail(e.toString(), e)
}
