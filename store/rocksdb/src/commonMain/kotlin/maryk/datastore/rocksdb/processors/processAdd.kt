package maryk.datastore.rocksdb.processors

import kotlinx.coroutines.flow.MutableSharedFlow
import maryk.core.clock.HLC
import maryk.core.extensions.bytes.toVarBytes
import maryk.core.models.IsRootDataModel
import maryk.core.processors.datastore.StorageTypeEnum.Embed
import maryk.core.processors.datastore.StorageTypeEnum.ListSize
import maryk.core.processors.datastore.StorageTypeEnum.MapSize
import maryk.core.processors.datastore.StorageTypeEnum.ObjectDelete
import maryk.core.processors.datastore.StorageTypeEnum.SetSize
import maryk.core.processors.datastore.StorageTypeEnum.TypeValue
import maryk.core.processors.datastore.StorageTypeEnum.Value
import maryk.core.processors.datastore.writeToStorage
import maryk.core.properties.definitions.IsComparableDefinition
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
import maryk.datastore.rocksdb.RocksDBDataStore
import maryk.datastore.rocksdb.TableColumnFamilies
import maryk.datastore.rocksdb.Transaction
import maryk.datastore.rocksdb.processors.helpers.setCreatedVersion
import maryk.datastore.rocksdb.processors.helpers.setIndexValue
import maryk.datastore.rocksdb.processors.helpers.setLatestVersion
import maryk.datastore.rocksdb.processors.helpers.setTypedValue
import maryk.datastore.rocksdb.processors.helpers.setUniqueIndexValue
import maryk.datastore.rocksdb.processors.helpers.setValue
import maryk.datastore.shared.TypeIndicator
import maryk.datastore.shared.UniqueException
import maryk.datastore.shared.updates.IsUpdateAction
import maryk.datastore.shared.updates.Update.Addition
import maryk.lib.recyclableByteArray
import maryk.rocksdb.rocksDBNotFound

internal suspend fun <DM : IsRootDataModel> processAdd(
    dataStore: RocksDBDataStore,
    dataModel: DM,
    transaction: Transaction,
    columnFamilies: TableColumnFamilies,
    dbIndex: UInt,
    key: Key<DM>,
    version: HLC,
    objectToAdd: Values<DM>,
    updateSharedFlow: MutableSharedFlow<IsUpdateAction>
): IsAddResponseStatus<DM> {
    return try {
        objectToAdd.validate()

        val mayExist = dataStore.db.keyMayExist(columnFamilies.keys, key.bytes, null)

        val exists = if (mayExist) {
            // Really check if item exists
            dataStore.db.get(columnFamilies.table, key.bytes, recyclableByteArray) != rocksDBNotFound
        } else false

        if (!exists) {
            val checksBeforeWrite = mutableListOf<() -> Unit>()

            // Create version bytes and last version ref
            val versionBytes = HLC.toStorageBytes(version)

            // Store first and last version
            setCreatedVersion(transaction, columnFamilies, key, versionBytes)
            setLatestVersion(transaction, columnFamilies, key, versionBytes)

            // Find new index values to write
            dataModel.Meta.indexes?.forEach { indexDefinition ->
                val indexReference = indexDefinition.referenceStorageByteArray.bytes
                val valueAndKeyBytes = indexDefinition.toStorageByteArrayForIndex(objectToAdd, key.bytes)
                    ?: return@forEach // skip if no complete values to index are found

                setIndexValue(transaction, columnFamilies, indexReference, valueAndKeyBytes, versionBytes)
            }

            objectToAdd.writeToStorage { type, reference, definition, value ->
                when (type) {
                    ObjectDelete -> Unit // Cannot happen on new add
                    Value -> {
                        val storableDefinition = Value.castDefinition(definition)
                        val valueBytes = storableDefinition.toStorageBytes(value, TypeIndicator.NoTypeIndicator.byte)

                        // If a unique index, check if exists, and then write
                        if ((definition is IsComparableDefinition<*, *>) && definition.unique) {
                            val uniqueReference = reference + valueBytes

                            checksBeforeWrite.add {
                                val uniqueCount =
                                    dataStore.db.get(columnFamilies.unique, uniqueReference, recyclableByteArray)
                                if (uniqueCount != rocksDBNotFound) {
                                    throw UniqueException(
                                        reference,
                                        Key<DM>(
                                            // Get the key at the end of the stored unique index value
                                            recyclableByteArray.copyOfRange(
                                                fromIndex = uniqueCount - key.size,
                                                toIndex = uniqueCount
                                            )
                                        )
                                    )
                                }
                            }

                            // Creates index reference on the table if it not exists so delete can find
                            // what values to delete from the unique indexes.
                            dataStore.createUniqueIndexIfNotExists(dbIndex, columnFamilies.unique, reference)
                            setUniqueIndexValue(columnFamilies, transaction, uniqueReference, versionBytes, key)
                        }

                        setValue(transaction, columnFamilies, key, reference, versionBytes, valueBytes)
                    }
                    ListSize,
                    SetSize,
                    MapSize -> setValue(
                        transaction,
                        columnFamilies,
                        key,
                        reference,
                        versionBytes,
                        (value as Int).toVarBytes()
                    )
                    TypeValue -> setTypedValue(
                        value,
                        definition,
                        transaction,
                        columnFamilies,
                        key,
                        reference,
                        versionBytes
                    )
                    Embed -> {
                        // Indicates value exists and is an embedded value object
                        // Is for the root of embed
                        val valueBytes = byteArrayOf(TypeIndicator.EmbedIndicator.byte, TRUE)
                        setValue(transaction, columnFamilies, key, reference, versionBytes, valueBytes)
                    }
                }
            }

            for (check in checksBeforeWrite) {
                check()
            }

            transaction.commit()

            val changes = listOf<IsChange>()

            updateSharedFlow.emit(
                Addition(dataModel, key, version.timestamp, objectToAdd.change(changes))
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
