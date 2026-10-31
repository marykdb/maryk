package maryk.datastore.shared.updates

import maryk.core.models.IsRootDataModel
import maryk.core.properties.types.Key
import maryk.core.query.requests.IsGetRequest
import maryk.core.query.responses.IsDataResponse
import maryk.core.values.Values
import maryk.datastore.shared.IsDataStore
import maryk.datastore.shared.updates.Update.Change

/** Update listener for get requests */
class UpdateListenerForGet<DM: IsRootDataModel, RP: IsDataResponse<DM>>(
    request: IsGetRequest<DM, RP>,
    response: RP
) : UpdateListener<DM, IsGetRequest<DM, RP>>(
    request,
    response
) {
    private val keyIndex = OrderedKeyIndex(request.keys, matchingKeys.value)

    override suspend fun process(
        update: Update<DM>,
        dataStore: IsDataStore
    ) {
        if (keyIndex.contains(update.key)) {
            update.process(this, dataStore, sendFlow)
        }
    }

    override fun addValues(key: Key<DM>, values: Values<DM>): Int? {
        if (!keyIndex.contains(key)) return null
        if (keyIndex.isPresent(key)) return matchingKeys.value.indexOf(key)
        keyIndex.add(key)
        matchingKeys.value = keyIndex.orderedPresentKeys()
        return matchingKeys.value.indexOf(key)
    }

    override fun removeKey(key: Key<DM>): Int = super.removeKey(key).also { index ->
        if (index >= 0) {
            matchingKeys.value = matchingKeys.value.filterNot { it == key }
            keyIndex.remove(key)
        }
    }

    override suspend fun changeOrder(
        change: Change<DM>,
        changedHandler: suspend (Int?, Boolean) -> Unit
    ) {
        matchingKeys.value.forEachIndexed { index, key ->
            if (key == change.key) changedHandler(index, false)
        }
    }
}

internal class OrderedKeyIndex<K>(
    requestedKeys: List<K>,
    presentKeys: List<K>,
) {
    private val requestedKeys = requestedKeys.toList()
    private val requested = requestedKeys.toHashSet()
    private val present = presentKeys.filterTo(mutableSetOf(), requested::contains)

    fun contains(key: K): Boolean = key in requested

    fun isPresent(key: K): Boolean = key in present

    fun add(key: K) {
        present += key
    }

    fun remove(key: K) {
        present -= key
    }

    fun orderedPresentKeys(): List<K> = requestedKeys.filter { it in present }
}
