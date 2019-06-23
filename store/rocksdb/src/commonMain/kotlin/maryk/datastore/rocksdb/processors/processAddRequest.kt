package maryk.datastore.rocksdb.processors

import maryk.core.clock.HLC
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
import maryk.core.properties.exceptions.AlreadySetException
import maryk.core.properties.exceptions.ValidationException
import maryk.core.properties.exceptions.ValidationUmbrellaException
import maryk.core.query.requests.AddRequest
import maryk.core.query.responses.AddResponse
import maryk.core.query.responses.statuses.AddSuccess
import maryk.core.query.responses.statuses.AlreadyExists
import maryk.core.query.responses.statuses.IsAddResponseStatus
import maryk.core.query.responses.statuses.ServerFail
import maryk.core.query.responses.statuses.ValidationFail
import maryk.datastore.rocksdb.HistoricTableColumnFamilies
import maryk.datastore.rocksdb.RocksDBDataStore
import maryk.datastore.shared.StoreAction
import maryk.datastore.shared.UniqueException
import maryk.rocksdb.WriteOptions
import maryk.rocksdb.use

internal typealias AddStoreAction<DM, P> = StoreAction<DM, P, AddRequest<DM, P>, AddResponse<DM>>
internal typealias AnyAddStoreAction = AddStoreAction<IsRootValuesDataModel<PropertyDefinitions>, PropertyDefinitions>

/** Processes an AddRequest in a [storeAction] into a [dataStore] */
@Suppress("UNUSED_VARIABLE", "UNUSED_PARAMETER")
internal fun <DM : IsRootValuesDataModel<P>, P : PropertyDefinitions> processAddRequest(
    storeAction: StoreAction<DM, P, AddRequest<DM, P>, AddResponse<DM>>,
    dataStore: RocksDBDataStore
) {
    val addRequest = storeAction.request
    val statuses = mutableListOf<IsAddResponseStatus<DM>>()

    if (addRequest.objects.isNotEmpty()) {
        val version = storeAction.version
        val columnFamilies = dataStore.getColumnFamilies(storeAction.dbIndex)

        for (objectToAdd in addRequest.objects) {
            try {
                objectToAdd.validate()

                val key = addRequest.dataModel.key(objectToAdd)

                val mayExist = dataStore.db.keyMayExist(columnFamilies.table, key.bytes, StringBuilder())

                val exists = if (mayExist) {
                    // Really check if item exists
                    dataStore.db.get(columnFamilies.table, key.bytes) != null
                } else false

                if (!exists) {
                    val checksBeforeWrite = mutableListOf<() -> Unit>()

                    // Create version bytes and last version ref
                    val versionBytes = HLC.toStorageBytes(version)
                    val lastVersionRef = byteArrayOf(*key.bytes, LAST_VERSION_INDICATOR)

                    dataStore.db.beginTransaction(WriteOptions()).use { transaction ->
                        // Store first and last version
                        transaction.put(columnFamilies.table, key.bytes, versionBytes)
                        transaction.put(columnFamilies.table, lastVersionRef, versionBytes)
                        if (columnFamilies is HistoricTableColumnFamilies) {
                            transaction.put(columnFamilies.historic.table, key.bytes, versionBytes)
                            transaction.put(columnFamilies.historic.table, lastVersionRef, versionBytes)
                        }

                        // Find new index values to write
                        addRequest.dataModel.indices?.forEach { indexDefinition ->
                            val indexReference = indexDefinition.toReferenceStorageByteArray()
                            val valueBytes = indexDefinition.toStorageByteArrayForIndex(objectToAdd, key.bytes)
                                ?: return@forEach // skip if no complete values to index are found

                            transaction.put(columnFamilies.index, byteArrayOf(*indexReference, *valueBytes), TRUE)
                            if (columnFamilies is HistoricTableColumnFamilies) {
                                transaction.put(
                                    columnFamilies.historic.index,
                                    byteArrayOf(*indexReference, *valueBytes, *versionBytes),
                                    TRUE
                                )
                            }
                        }

                        objectToAdd.writeToStorage { type, reference, definition, value ->
                            when (type) {
                                ObjectDelete -> {} // Cannot happen on new add
                                Value -> {
                                    val storableDefinition = Value.castDefinition(definition)

                                    val valueBytes = storableDefinition.toStorageBytes(value)

                                    // If a unique index, check if exists, and then write
                                    if ((definition is IsComparableDefinition<*, *>) && definition.unique) {
                                        val uniqueReference = byteArrayOf(*reference, *valueBytes)

                                        checksBeforeWrite.add {
                                            // Since it is an addition we only need to check the current uniques
                                            dataStore.db.get(columnFamilies.unique, uniqueReference)?.let {
                                                throw UniqueException(reference)
                                            }
                                        }

                                        transaction.put(columnFamilies.unique, uniqueReference, key.bytes)
                                        if (columnFamilies is HistoricTableColumnFamilies) {
                                            transaction.put(
                                                columnFamilies.historic.unique,
                                                byteArrayOf(*uniqueReference, *versionBytes),
                                                key.bytes
                                            )
                                        }
                                    }

                                    transaction.put(
                                        columnFamilies.table,
                                        byteArrayOf(*key.bytes, *reference),
                                        byteArrayOf(*versionBytes, *valueBytes)
                                    )

                                    if (columnFamilies is HistoricTableColumnFamilies) {
                                        transaction.put(
                                            columnFamilies.historic.table,
                                            byteArrayOf(*key.bytes, *reference, *versionBytes),
                                            valueBytes
                                        )
                                    }
                                }
                                ListSize -> TODO("LIST SIZE")
                                SetSize -> TODO("SET SIZE")
                                MapSize -> TODO("MAP SIZE")
                                TypeValue -> TODO("TYPE VALUE")
                                Embed -> TODO("EMBED")
                            }
                        }

                        for (check in checksBeforeWrite) {
                            check()
                        }

                        transaction.commit()
                    }

                    statuses.add(
                        AddSuccess(key, version.timestamp, listOf())
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
                            AlreadySetException(ref)
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
