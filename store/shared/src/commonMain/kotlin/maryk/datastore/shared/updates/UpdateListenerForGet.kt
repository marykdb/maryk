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
        val insertionIndex = keyIndex.insertionIndex(matchingKeys.value, key)

        matchingKeys.value = buildList {
            addAll(matchingKeys.value)
            add(insertionIndex, key)
        }
        keyIndex.add(key)
        return insertionIndex
    }

    override fun removeKey(key: Key<DM>): Int = super.removeKey(key).also { index ->
        if (index >= 0) keyIndex.remove(key)
    }

    override suspend fun changeOrder(change: Change<DM>, changedHandler: suspend (Int?, Boolean) -> Unit) {
        val keyIndex = matchingKeys.value.indexOfFirst { it compareTo change.key == 0 }

        if (keyIndex >= 0) {
            changedHandler(if (keyIndex >= 0) keyIndex else null, false)
        }
    }
}

internal class OrderedKeyIndex<K>(
    requestedKeys: List<K>,
    presentKeys: List<K>,
) {
    private val requestedOrder = buildMap {
        requestedKeys.forEachIndexed { index, key ->
            if (key !in this) put(key, index)
        }
    }
    private val present = presentKeys.filterTo(mutableSetOf(), requestedOrder::containsKey)

    fun contains(key: K): Boolean = requestedOrder.containsKey(key)

    fun isPresent(key: K): Boolean = key in present

    fun add(key: K) {
        present += key
    }

    fun remove(key: K) {
        present -= key
    }

    fun insertionIndex(currentKeys: List<K>, key: K): Int {
        val targetOrder = requestedOrder.getValue(key)
        var low = 0
        var high = currentKeys.size
        while (low < high) {
            val middle = (low + high) ushr 1
            val middleOrder = requestedOrder[currentKeys[middle]] ?: Int.MAX_VALUE
            if (middleOrder < targetOrder) low = middle + 1 else high = middle
        }
        return low
    }
}
