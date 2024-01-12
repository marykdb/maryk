package maryk.datastore.hbase.processors

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.future.await
import kotlinx.coroutines.withContext
import maryk.core.clock.HLC
import maryk.core.models.IsRootDataModel
import maryk.core.processors.datastore.StorageTypeEnum
import maryk.core.processors.datastore.writeToStorage
import maryk.core.properties.exceptions.AlreadyExistsException
import maryk.core.properties.exceptions.ValidationException
import maryk.core.properties.exceptions.ValidationUmbrellaException
import maryk.core.properties.types.Key
import maryk.core.query.changes.IsChange
import maryk.core.query.responses.statuses.AddSuccess
import maryk.core.query.responses.statuses.AlreadyExists
import maryk.core.query.responses.statuses.IsAddResponseStatus
import maryk.core.query.responses.statuses.ServerFail
import maryk.core.query.responses.statuses.ValidationFail
import maryk.core.values.Values
import maryk.datastore.hbase.HbaseDataStore
import maryk.datastore.hbase.MetaColumns
import maryk.datastore.hbase.dataColumnFamily
import maryk.datastore.hbase.helpers.countValueAsBytes
import maryk.datastore.hbase.helpers.setTypedValue
import maryk.datastore.hbase.trueIndicator
import maryk.datastore.shared.TypeIndicator
import maryk.datastore.shared.UniqueException
import maryk.datastore.shared.updates.IsUpdateAction
import maryk.datastore.shared.updates.Update
import org.apache.hadoop.hbase.client.AdvancedScanResultConsumer
import org.apache.hadoop.hbase.client.AsyncTable
import org.apache.hadoop.hbase.client.CheckAndMutate
import org.apache.hadoop.hbase.client.Put

@Suppress("UNUSED_PARAMETER")
internal suspend fun <DM : IsRootDataModel> processAdd(
    dataStore: HbaseDataStore,
    dataModel: DM,
    dbIndex: UInt,
    table: AsyncTable<AdvancedScanResultConsumer>,
    key: Key<DM>,
    version: HLC,
    objectToAdd: Values<DM>,
    updateSharedFlow: MutableSharedFlow<IsUpdateAction>
): IsAddResponseStatus<DM> {
    return try {
        objectToAdd.validate()

        val checksBeforeWrite = mutableListOf<() -> Unit>()

        // Create version bytes and last version ref
        val versionBytes = HLC.toStorageBytes(version)

        val put = Put(key.bytes).setTimestamp(version.timestamp.toLong())

        // Store first and last version
        put.addColumn(dataColumnFamily, MetaColumns.CreatedVersion.byteArray, version.timestamp.toLong(), versionBytes)
        put.addColumn(dataColumnFamily, MetaColumns.LatestVersion.byteArray, version.timestamp.toLong(), versionBytes)

//            // Find new index values to write
//            dataModel.Meta.indices?.forEach { indexDefinition ->
//                val indexReference = indexDefinition.referenceStorageByteArray.bytes
//                val valueAndKeyBytes = indexDefinition.toStorageByteArrayForIndex(objectToAdd, key.bytes)
//                    ?: return@forEach // skip if no complete values to index are found
//
//                setIndexValue(transaction, columnFamilies, indexReference, valueAndKeyBytes, versionBytes)
//            }
//
        objectToAdd.writeToStorage { type, reference, definition, value ->
            when (type) {
                StorageTypeEnum.ObjectDelete -> Unit // Cannot happen on new add
                StorageTypeEnum.Value -> {
                    val storableDefinition = StorageTypeEnum.Value.castDefinition(definition)
                    val valueBytes = storableDefinition.toStorageBytes(value, TypeIndicator.NoTypeIndicator.byte)

//                        // If a unique index, check if exists, and then write
//                        if ((definition is IsComparableDefinition<*, *>) && definition.unique) {
//                            val uniqueReference = byteArrayOf(*reference, *valueBytes)
//
//                            checksBeforeWrite.add {
//                                // Since it is an addition we only need to check the current uniques
//                                val uniqueCount =
//                                    dataStore.db.get(columnFamilies.unique, uniqueReference, recyclableByteArray)
//                                if (uniqueCount != RocksDB.NOT_FOUND) {
//                                    throw UniqueException(
//                                        reference,
//                                        Key<DM>(
//                                            // Get the key at the end of the stored unique index value
//                                            recyclableByteArray.copyOfRange(
//                                                fromIndex = uniqueCount - key.size,
//                                                toIndex = uniqueCount
//                                            )
//                                        )
//                                    )
//                                }
//                            }
//
//                            // Creates index reference on the table if it not exists so delete can find
//                            // what values to delete from the unique indices.
//                            dataStore.createUniqueIndexIfNotExists(dbIndex, columnFamilies.unique, reference)
//                            setUniqueIndexValue(columnFamilies, transaction, uniqueReference, versionBytes, key)
//                        }

                    put.addColumn(dataColumnFamily, reference, version.timestamp.toLong(), valueBytes)
                }
                StorageTypeEnum.ListSize,
                StorageTypeEnum.SetSize,
                StorageTypeEnum.MapSize ->
                    put.addColumn(dataColumnFamily, reference, version.timestamp.toLong(), countValueAsBytes(value as Int))
                StorageTypeEnum.TypeValue ->
                    setTypedValue(value, definition, put, reference)
                StorageTypeEnum.Embed -> {
                    // Indicates value exists and is an embedded value object
                    // Is for the root of embed
                    val valueBytes = byteArrayOf(TypeIndicator.EmbedIndicator.byte, *trueIndicator)
                    put.addColumn(dataColumnFamily, reference, version.timestamp.toLong(), valueBytes)
                }
            }
        }

        for (check in checksBeforeWrite) {
            check()
        }

        val response = withContext(Dispatchers.IO) {
            table.checkAndMutate(
                CheckAndMutate.newBuilder(key.bytes).apply {
                    ifNotExists(dataColumnFamily, MetaColumns.CreatedVersion.byteArray)
                }.build(put)
            ).await()
        }

        if (response.isSuccess) {
            val changes = emptyList<IsChange>()

            updateSharedFlow.emit(
                Update.Addition(dataModel, key, version.timestamp, objectToAdd.change(changes))
            )

            AddSuccess(key, version.timestamp, changes)
        } else {
            AlreadyExists(key)
        }
    } catch (ve: ValidationUmbrellaException) {
        ValidationFail(ve)
    } catch (ve: ValidationException) {
        ValidationFail(listOf(ve))
    } catch (ue: UniqueException) {
        var index = 0
        val ref = dataModel.getPropertyReferenceByStorageBytes(
            ue.reference.size,
            { ue.reference[index++] }
        )

        ValidationFail(
            listOf(
                AlreadyExistsException(ref, ue.key)
            )
        )
    } catch (e: Throwable) {
        ServerFail(e.toString(), e)
    }
}
