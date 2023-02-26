package maryk.datastore.shared.updates

import kotlinx.atomicfu.AtomicRef
import kotlinx.atomicfu.atomic
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.onStart
import maryk.core.properties.IsRootModel
import maryk.core.properties.types.Key
import maryk.core.query.requests.IsFetchRequest
import maryk.core.query.responses.ChangesResponse
import maryk.core.query.responses.IsDataResponse
import maryk.core.query.responses.UpdatesResponse
import maryk.core.query.responses.ValuesResponse
import maryk.core.query.responses.updates.InitialChangesUpdate
import maryk.core.query.responses.updates.InitialValuesUpdate
import maryk.core.query.responses.updates.IsUpdateResponse
import maryk.core.query.responses.updates.OrderedKeysUpdate
import maryk.core.values.Values
import maryk.datastore.shared.IsDataStore
import maryk.datastore.shared.updates.Update.Change

/** Listener for updates on a data store */
abstract class UpdateListener<DM: IsRootModel, RQ: IsFetchRequest<DM, *>>(
    val request: RQ,
    val response: IsDataResponse<DM>
) {
    protected val sendFlow = MutableSharedFlow<IsUpdateResponse<DM>>(extraBufferCapacity = 64)

    val matchingKeys: AtomicRef<List<Key<DM>>>
    private val lastResponseVersion: ULong

    init {
        when (response) {
            is UpdatesResponse<DM> -> {
                matchingKeys = atomic((response.updates.firstOrNull() as? OrderedKeysUpdate<DM>)?.keys ?: listOf())
                lastResponseVersion = (response.updates.firstOrNull() as? OrderedKeysUpdate<DM>)?.version ?: 0uL
            }
            is ValuesResponse<DM> -> {
                matchingKeys = atomic(response.values.map { it.key })
                lastResponseVersion = response.values.maxByOrNull { it.lastVersion }?.lastVersion ?: 0uL
            }
            is ChangesResponse<DM> -> {
                matchingKeys = atomic(response.changes.map { it.key })
                lastResponseVersion = response.changes.fold(0uL) { acc, value ->
                    maxOf(acc, value.changes.maxByOrNull { it.version }?.version ?: 0uL)
                }
            }
            else -> throw Exception("Unknown response type $response. Cannot process its values")
        }
    }

    // True if the listener filters on mutable values
    val filterContainsMutableValues: Boolean = request.where?.singleReference {
        !it.propertyDefinition.required || !it.propertyDefinition.final
    } != null

    /** Process [update] and sent out responses over channel */
    abstract suspend fun process(
        update: Update<DM>,
        dataStore: IsDataStore
    )

    /** Add [values] at [key] and return sort index or null if it should not be added */
    abstract fun addValues(key: Key<DM>, values: Values<DM>): Int?

    /** Remove [key] from local index */
    open fun removeKey(key: Key<DM>): Int {
        val index = matchingKeys.value.indexOf(key)

        if (index >= 0) {
            matchingKeys.value = matchingKeys.value.filterIndexed { i, _ -> i != index }
        }
        return index
    }

    /**
     * Change order for values if needed and return new or current index.
     * Calls changedHandler with an index at which index the value should be and boolean if order changed or
     * null if it was deleted
     */
    abstract suspend fun changeOrder(change: Change<DM>, changedHandler: suspend (Int?, Boolean) -> Unit)

    /** Get flow with update responses */
    fun getFlow() = sendFlow.onStart {
        when (response) {
            is UpdatesResponse<DM> -> {
                for (update in response.updates) {
                    emit(update)
                }
            }
            is ValuesResponse<DM> -> emit(
                InitialValuesUpdate(
                    version = lastResponseVersion,
                    values = response.values
                )
            )
            is ChangesResponse<DM> -> emit(
                InitialChangesUpdate(
                    version = lastResponseVersion,
                    changes = response.changes
                )
            )
            else -> throw Exception("Unknown response type $response. Cannot process its values")
        }
    }

    fun close() {
    }
}
