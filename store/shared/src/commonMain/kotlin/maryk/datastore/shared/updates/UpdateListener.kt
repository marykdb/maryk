package maryk.datastore.shared.updates

import kotlinx.atomicfu.AtomicRef
import kotlinx.atomicfu.atomic
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.onStart
import maryk.core.models.IsRootDataModel
import maryk.core.properties.IsValuesPropertyDefinitions
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
abstract class UpdateListener<DM: IsRootDataModel<P>, P: IsValuesPropertyDefinitions, RQ: IsFetchRequest<DM, P, *>>(
    val request: RQ,
    val response: IsDataResponse<DM, P>
) {
    protected val sendFlow = MutableSharedFlow<IsUpdateResponse<DM, P>>(extraBufferCapacity = 64)

    val matchingKeys: AtomicRef<List<Key<DM>>>
    private val lastResponseVersion: ULong

    init {
        when (response) {
            is UpdatesResponse<DM, P> -> {
                matchingKeys = atomic((response.updates.firstOrNull() as? OrderedKeysUpdate<DM, P>)?.keys ?: listOf())
                lastResponseVersion = (response.updates.firstOrNull() as? OrderedKeysUpdate<DM, P>)?.version ?: 0uL
            }
            is ValuesResponse<DM, P> -> {
                matchingKeys = atomic(response.values.map { it.key })
                lastResponseVersion = response.values.maxByOrNull { it.lastVersion }?.lastVersion ?: 0uL
            }
            is ChangesResponse<DM, P> -> {
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
        update: Update<DM, P>,
        dataStore: IsDataStore
    )

    /** Add [values] at [key] and return sort index or null if it should not be added */
    abstract fun addValues(key: Key<DM>, values: Values<P>): Int?

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
    abstract suspend fun changeOrder(change: Change<DM, P>, changedHandler: suspend (Int?, Boolean) -> Unit)

    /** Get flow with update responses */
    fun getFlow() = sendFlow.onStart {
        when (response) {
            is UpdatesResponse<DM, P> -> {
                for (update in response.updates) {
                    emit(update)
                }
            }
            is ValuesResponse<DM, P> -> emit(
                InitialValuesUpdate(
                    version = lastResponseVersion,
                    values = response.values
                )
            )
            is ChangesResponse<DM, P> -> emit(
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
