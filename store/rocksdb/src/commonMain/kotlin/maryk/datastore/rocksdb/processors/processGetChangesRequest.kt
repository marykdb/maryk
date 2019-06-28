package maryk.datastore.rocksdb.processors

import maryk.core.extensions.bytes.toULong
import maryk.core.models.IsRootValuesDataModel
import maryk.core.properties.PropertyDefinitions
import maryk.core.query.changes.DataObjectVersionedChange
import maryk.core.query.requests.GetChangesRequest
import maryk.core.query.responses.ChangesResponse
import maryk.datastore.rocksdb.RocksDBDataStore
import maryk.datastore.shared.StoreAction
import maryk.datastore.shared.checkToVersion
import maryk.rocksdb.ReadOptions
import maryk.rocksdb.WriteOptions
import maryk.rocksdb.use

internal typealias GetChangesStoreAction<DM, P> = StoreAction<DM, P, GetChangesRequest<DM, P>, ChangesResponse<DM>>
internal typealias AnyGetChangesStoreAction = GetChangesStoreAction<IsRootValuesDataModel<PropertyDefinitions>, PropertyDefinitions>

/** Processes a GetChangesRequest in a [storeAction] into a [dataStore] */
internal fun <DM : IsRootValuesDataModel<P>, P : PropertyDefinitions> processGetChangesRequest(
    storeAction: GetChangesStoreAction<DM, P>,
    dataStore: RocksDBDataStore
) {
    val getRequest = storeAction.request
    val objectChanges = mutableListOf<DataObjectVersionedChange<DM>>()
    val columnFamilies = dataStore.getColumnFamilies(storeAction.dbIndex)

    getRequest.checkToVersion(dataStore.keepAllVersions)

    WriteOptions().use { writeOptions ->
        dataStore.db.beginTransaction(writeOptions).use { transaction ->
            ReadOptions().use { readOptions ->
                // On iteration, dont iterate past the prefix/key
                readOptions.setPrefixSameAsStart(true)
                keyWalk@for (key in getRequest.keys) {
                    val mayExist = dataStore.db.keyMayExist(columnFamilies.table, key.bytes, StringBuilder())
                    if (mayExist) {
                        val creationVersion = transaction.get(columnFamilies.table, readOptions, key.bytes)?.toULong()

                        if (creationVersion != null) {
                            if (getRequest.shouldBeFiltered(transaction, columnFamilies, readOptions, key, creationVersion, getRequest.toVersion)) {
                                continue@keyWalk
                            }

                            getRequest.dataModel.readTransactionIntoObjectChanges(
                                transaction,
                                readOptions,
                                creationVersion,
                                columnFamilies,
                                key,
                                getRequest.select,
                                getRequest.fromVersion,
                                getRequest.toVersion
                            )?.also {
                                // Only add if not null
                                objectChanges += it
                            }
                        }
                    }
                }
            }
        }
    }

    storeAction.response.complete(
        ChangesResponse(
            dataModel = getRequest.dataModel,
            changes = objectChanges
        )
    )
}
