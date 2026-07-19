package maryk.datastore.indexeddb.processors

import maryk.core.models.IsRootDataModel
import maryk.core.processors.datastore.StorageTypeEnum
import maryk.core.properties.definitions.IsPropertyDefinition
import maryk.core.properties.types.Bytes
import maryk.core.query.ValuesWithMetaData
import maryk.core.query.changes.IsChange
import maryk.core.query.requests.AddRequest
import maryk.core.query.requests.ChangeRequest
import maryk.core.query.requests.DeleteRequest
import maryk.core.query.requests.GetChangesRequest
import maryk.core.query.requests.GetRequest
import maryk.core.query.requests.GetUpdatesRequest
import maryk.core.query.requests.ScanChangesRequest
import maryk.core.query.requests.ScanRequest
import maryk.core.query.requests.ScanUpdateHistoryRequest
import maryk.core.query.requests.ScanUpdatesRequest
import maryk.core.query.requests.add
import maryk.core.query.responses.AddResponse
import maryk.core.query.responses.ChangeResponse
import maryk.core.query.responses.ChangesResponse
import maryk.core.query.responses.DataFetchType
import maryk.core.query.responses.DeleteResponse
import maryk.core.query.responses.UpdateResponse
import maryk.core.query.responses.UpdatesResponse
import maryk.core.query.responses.ValuesResponse
import maryk.core.query.responses.updates.ProcessResponse
import maryk.datastore.indexeddb.IndexedDbDataStore
import maryk.datastore.shared.StoreAction



internal typealias AddStoreAction<DM> = StoreAction<DM, AddRequest<DM>, AddResponse<DM>>
internal typealias ChangeStoreAction<DM> = StoreAction<DM, ChangeRequest<DM>, ChangeResponse<DM>>
internal typealias DeleteStoreAction<DM> = StoreAction<DM, DeleteRequest<DM>, DeleteResponse<DM>>
internal typealias GetChangesStoreAction<DM> = StoreAction<DM, GetChangesRequest<DM>, ChangesResponse<DM>>
internal typealias GetStoreAction<DM> = StoreAction<DM, GetRequest<DM>, ValuesResponse<DM>>
internal typealias GetUpdatesStoreAction<DM> = StoreAction<DM, GetUpdatesRequest<DM>, UpdatesResponse<DM>>
internal typealias ProcessUpdateResponseStoreAction<DM> = StoreAction<DM, UpdateResponse<DM>, ProcessResponse<DM>>
internal typealias ScanChangesStoreAction<DM> = StoreAction<DM, ScanChangesRequest<DM>, ChangesResponse<DM>>
internal typealias ScanStoreAction<DM> = StoreAction<DM, ScanRequest<DM>, ValuesResponse<DM>>
internal typealias ScanUpdateHistoryStoreAction<DM> = StoreAction<DM, ScanUpdateHistoryRequest<DM>, UpdatesResponse<DM>>
internal typealias ScanUpdatesStoreAction<DM> = StoreAction<DM, ScanUpdatesRequest<DM>, UpdatesResponse<DM>>

internal data class ScanUpdateRows<DM : IsRootDataModel>(
    val rows: List<ValuesWithMetaData<DM>>,
    val sortingKeys: List<Bytes>?,
    val dataFetchType: DataFetchType,
)

internal fun ScanUpdatesRequest<*>.canUseUpdateHistoryIndex() =
    order == null && startKey == null && includeStart && fromVersion == 0uL && toVersion == null && maxVersions == 1u

internal data class CurrentStateStoragePlan(
    val tableRows: List<Pair<ByteArray, ByteArray>>,
    val indexRows: List<ByteArray>,
    val uniqueRows: List<IndexedDbUniqueRow>,
)

internal data class IndexedDbUniqueRow(
    val uniqueKey: ByteArray,
    val keyBytes: ByteArray,
    val qualifier: ByteArray,
    val candidateKeys: List<ByteArray>,
)

internal data class MaterializedChanges(
    val appliedChanges: List<IsChange>,
    val generatedChanges: List<IsChange>,
)

internal data class StorageRowToWrite(
    val qualifier: ByteArray,
    val encodedValue: ByteArray,
    val definition: IsPropertyDefinition<*>,
    val type: StorageTypeEnum<IsPropertyDefinition<*>>,
)

internal fun IndexedDbDataStore.modelWriteStoreNames(
    keyStoreName: String,
    tableStoreName: String,
    indexStoreName: String,
    uniqueStoreName: String,
    changeStoreName: String,
    updateHistoryStoreName: String,
    historicTableStoreName: String,
    historicIndexStoreName: String,
    historicUniqueStoreName: String,
    historicIndexCleanupStoreName: String,
    historicUniqueCleanupStoreName: String,
): Set<String> = buildSet {
    add(keyStoreName)
    add(tableStoreName)
    add(indexStoreName)
    add(uniqueStoreName)
    add(changeStoreName)
    if (keepUpdateHistoryIndex) {
        add(updateHistoryStoreName)
    }
    if (keepAllVersions) {
        add(historicTableStoreName)
        add(historicIndexStoreName)
        add(historicUniqueStoreName)
        add(historicIndexCleanupStoreName)
        add(historicUniqueCleanupStoreName)
    }
}
