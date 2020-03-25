package maryk.datastore.rocksdb.processors

import kotlinx.coroutines.channels.SendChannel
import maryk.core.clock.HLC
import maryk.core.extensions.bytes.toVarBytes
import maryk.core.models.IsRootValuesDataModel
import maryk.core.models.key
import maryk.core.processors.datastore.StorageTypeEnum.Embed
import maryk.core.processors.datastore.StorageTypeEnum.ListSize
import maryk.core.processors.datastore.StorageTypeEnum.MapSize
import maryk.core.processors.datastore.StorageTypeEnum.ObjectDelete
import maryk.core.processors.datastore.StorageTypeEnum.SetSize
import maryk.core.processors.datastore.StorageTypeEnum.TypeValue
import maryk.core.processors.datastore.StorageTypeEnum.Value
import maryk.core.processors.datastore.writeToStorage
import maryk.core.properties.PropertyDefinitions
import maryk.core.properties.definitions.IsComparableDefinition
import maryk.core.properties.exceptions.AlreadyExistsException
import maryk.core.properties.exceptions.ValidationException
import maryk.core.properties.exceptions.ValidationUmbrellaException
import maryk.core.properties.types.Key
import maryk.core.query.changes.IsChange
import maryk.core.query.requests.AddRequest
import maryk.core.query.responses.AddResponse
import maryk.core.query.responses.statuses.AddSuccess
import maryk.core.query.responses.statuses.AlreadyExists
import maryk.core.query.responses.statuses.IsAddResponseStatus
import maryk.core.query.responses.statuses.ServerFail
import maryk.core.query.responses.statuses.ValidationFail
import maryk.datastore.rocksdb.RocksDBDataStore
import maryk.datastore.rocksdb.Transaction
import maryk.datastore.rocksdb.processors.helpers.setCreatedVersion
import maryk.datastore.rocksdb.processors.helpers.setIndexValue
import maryk.datastore.rocksdb.processors.helpers.setLatestVersion
import maryk.datastore.rocksdb.processors.helpers.setTypedValue
import maryk.datastore.rocksdb.processors.helpers.setUniqueIndexValue
import maryk.datastore.rocksdb.processors.helpers.setValue
import maryk.datastore.shared.StoreAction
import maryk.datastore.shared.UniqueException
import maryk.datastore.shared.updates.Update
import maryk.datastore.shared.updates.Update.Addition
import maryk.lib.recyclableByteArray
import maryk.rocksdb.rocksDBNotFound
import maryk.rocksdb.use

internal typealias AddStoreAction<DM, P> = StoreAction<DM, P, AddRequest<DM, P>, AddResponse<DM>>
internal typealias AnyAddStoreAction = AddStoreAction<IsRootValuesDataModel<PropertyDefinitions>, PropertyDefinitions>

/** Processes an AddRequest in a [storeAction] into a [dataStore] */
@Suppress("UNUSED_VARIABLE", "UNUSED_PARAMETER")
internal suspend fun <DM : IsRootValuesDataModel<P>, P : PropertyDefinitions> processAddRequest(
    storeAction: StoreAction<DM, P, AddRequest<DM, P>, AddResponse<DM>>,
    dataStore: RocksDBDataStore,
    updateSendChannel: SendChannel<Update<DM, P>>
) {
    val addRequest = storeAction.request
    val statuses = mutableListOf<IsAddResponseStatus<DM>>()

    if (addRequest.objects.isNotEmpty()) {
        val version = storeAction.version
        val dbIndex = dataStore.getDataModelId(addRequest.dataModel)
        val columnFamilies = dataStore.getColumnFamilies(dbIndex)

        for (objectToAdd in addRequest.objects) {
            try {
                objectToAdd.validate()

                val key = addRequest.dataModel.key(objectToAdd)
                val mayExist = dataStore.db.keyMayExist(columnFamilies.keys, key.bytes, null)

                val exists = if (mayExist) {
                    // Really check if item exists
                    dataStore.db.get(columnFamilies.table, key.bytes, recyclableByteArray) != rocksDBNotFound
                } else false

                if (!exists) {
                    val checksBeforeWrite = mutableListOf<() -> Unit>()

                    // Create version bytes and last version ref
                    val versionBytes = HLC.toStorageBytes(version)

                    Transaction(dataStore).use { transaction ->
                        // Store first and last version
                        setCreatedVersion(transaction, columnFamilies, key, versionBytes)
                        setLatestVersion(transaction, columnFamilies, key, versionBytes)

                        // Find new index values to write
                        addRequest.dataModel.indices?.forEach { indexDefinition ->
                            val indexReference = indexDefinition.toReferenceStorageByteArray()
                            val valueAndKeyBytes = indexDefinition.toStorageByteArrayForIndex(objectToAdd, key.bytes)
                                ?: return@forEach // skip if no complete values to index are found

                            setIndexValue(transaction, columnFamilies, indexReference, valueAndKeyBytes, versionBytes)
                        }

                        objectToAdd.writeToStorage { type, reference, definition, value ->
                            when (type) {
                                ObjectDelete -> {} // Cannot happen on new add
                                Value -> {
                                    val storableDefinition = Value.castDefinition(definition)
                                    val valueBytes = storableDefinition.toStorageBytes(value, NO_TYPE_INDICATOR)

                                    // If a unique index, check if exists, and then write
                                    if ((definition is IsComparableDefinition<*, *>) && definition.unique) {
                                        val uniqueReference = byteArrayOf(*reference, *valueBytes)

                                        checksBeforeWrite.add {
                                            // Since it is an addition we only need to check the current uniques
                                            val uniqueCount = dataStore.db.get(columnFamilies.unique, uniqueReference, recyclableByteArray)
                                            if (uniqueCount != rocksDBNotFound) {
                                                throw UniqueException(
                                                    reference,
                                                    Key<DM>(
                                                        // Get the key at the end of the stored unique index value
                                                        recyclableByteArray.copyOfRange(fromIndex = uniqueCount - key.size, toIndex = uniqueCount)
                                                    )
                                                )
                                            }
                                        }

                                        // Creates index reference on the table if it not exists so delete can find
                                        // what values to delete from the unique indices.
                                        dataStore.createUniqueIndexIfNotExists(dbIndex, columnFamilies.unique, reference)
                                        setUniqueIndexValue(columnFamilies, transaction, uniqueReference, versionBytes, key)
                                    }

                                    setValue(transaction, columnFamilies, key, reference, versionBytes, valueBytes)
                                }
                                ListSize,
                                SetSize,
                                MapSize -> setValue(transaction, columnFamilies, key, reference, versionBytes, (value as Int).toVarBytes())
                                TypeValue -> setTypedValue(value, definition, transaction, columnFamilies, key, reference, versionBytes)
                                Embed -> {
                                    // Indicates value exists and is an embedded value object
                                    // Is for the root of embed
                                    val valueBytes = byteArrayOf(EMBED_INDICATOR, TRUE)
                                    setValue(transaction, columnFamilies, key, reference, versionBytes, valueBytes)
                                }
                            }
                        }

                        for (check in checksBeforeWrite) {
                            check()
                        }

                        transaction.commit()
                    }

                    val changes = listOf<IsChange>()

                    updateSendChannel.send(
                        Addition(addRequest.dataModel, key, version, objectToAdd.change(changes))
                    )

                    statuses.add(
                        AddSuccess(key, version.timestamp, changes)
                    )
                } else {
                    statuses.add(
                        AlreadyExists(key)
                    )
                }
            } catch (ve: ValidationUmbrellaException) {
                statuses.add(
                    ValidationFail(ve)
                )
            } catch (ve: ValidationException) {
                statuses.add(
                    ValidationFail(listOf(ve))
                )
            } catch (ue: UniqueException) {
                var index = 0
                val ref = addRequest.dataModel.getPropertyReferenceByStorageBytes(
                    ue.reference.size,
                    { ue.reference[index++] }
                )

                statuses.add(
                    ValidationFail(
                        listOf(
                            AlreadyExistsException(ref, ue.key)
                        )
                    )
                )
            } catch (e: Throwable) {
                statuses.add(
                    ServerFail(e.toString(), e)
                )
            }
        }
    }

    storeAction.response.complete(
        AddResponse(
            storeAction.request.dataModel,
            statuses
        )
    )
}
