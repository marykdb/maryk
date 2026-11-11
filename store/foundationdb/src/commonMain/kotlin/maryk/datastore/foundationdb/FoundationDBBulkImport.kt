package maryk.datastore.foundationdb

import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import maryk.core.clock.HLC
import maryk.core.models.IsRootDataModel
import maryk.core.models.key
import maryk.core.query.requests.AddRequest
import maryk.core.query.requests.add
import maryk.core.query.requests.IsStoreRequest
import maryk.core.query.responses.statuses.IsAddResponseStatus
import maryk.core.query.responses.AddResponse
import maryk.datastore.foundationdb.processors.processAdd
import maryk.datastore.shared.currentTransaction
import maryk.datastore.shared.updates.Update
import kotlin.coroutines.AbstractCoroutineContextElement
import kotlin.coroutines.CoroutineContext

internal class FoundationDBBulkImportContext(val owner: FoundationDBDataStore) : AbstractCoroutineContextElement(Key) {
    companion object Key : CoroutineContext.Key<FoundationDBBulkImportContext>
    val updates = mutableListOf<Update<*>>()
}

internal class FoundationDBBulkAddRequest<DM : IsRootDataModel>(
    val request: AddRequest<DM>,
    val concurrency: Int,
) : IsStoreRequest<DM, AddResponse<DM>> {
    override val dataModel: DM get() = request.dataModel
}

/**
 * Import distinct records with bounded native-transaction concurrency. Each record
 * commits independently; statuses and local notifications preserve input order.
 */
suspend fun <DM : IsRootDataModel> FoundationDBDataStore.bulkAdd(
    request: AddRequest<DM>,
    concurrency: Int = 8,
): AddResponse<DM> {
    require(concurrency in 1..64)
    check(currentTransaction(this) == null) { "Bulk import cannot run inside a transaction scope" }
    val keys = request.keysForObjects ?: request.objects.map { request.dataModel.key(it) }
    require(keys.toSet().size == keys.size) { "Bulk import requires distinct record keys" }
    return execute(FoundationDBBulkAddRequest(request.dataModel.add(*keys.zip(request.objects).toTypedArray()), concurrency))
}

internal suspend fun <DM : IsRootDataModel> FoundationDBDataStore.processBulkAdd(
    request: FoundationDBBulkAddRequest<DM>,
    version: HLC,
): AddResponse<DM> = coroutineScope {
    require(version.timestamp <= ULong.MAX_VALUE - request.request.objects.size.toULong())
    val model = request.dataModel
    val directories = getTableDirs(model)
    val statuses = mutableListOf<IsAddResponseStatus<DM>>()
    for (batch in request.request.objects.withIndex().toList().chunked(request.concurrency)) {
        val completed = batch.map { (index, values) -> async {
            val pending = FoundationDBBulkImportContext(this@processBulkAdd)
            val status = withContext(pending) {
                processAdd(directories, model, request.request.keysForObjects!![index],
                    HLC(version.timestamp + index.toULong() + 1uL), values)
            }
            status to pending.updates
        } }.awaitAll()
        // Bound retained notification payloads to one concurrency window.
        for ((status, updates) in completed) {
            statuses += status
            for (update in updates) emitUpdate(update)
        }
    }
    AddResponse(model, statuses)
}
