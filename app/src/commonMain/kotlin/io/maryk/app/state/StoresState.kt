package io.maryk.app.state

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import io.maryk.app.config.StoreDefinition
import io.maryk.app.config.StoreRepository

@Stable
class StoresState(
    private val repository: StoreRepository,
) {
    var stores by mutableStateOf<List<StoreDefinition>>(emptyList())
        private set

    var showStoreEditor by mutableStateOf(false)
        private set

    var editingStore by mutableStateOf<StoreDefinition?>(null)
        private set

    fun loadStores() {
        stores = repository.load().sortedBy { it.name.lowercase() }
    }

    fun openStoreEditor(store: StoreDefinition? = null) {
        editingStore = store
        showStoreEditor = true
    }

    fun closeStoreEditor() {
        editingStore = null
        showStoreEditor = false
    }

    fun upsertStore(definition: StoreDefinition) {
        val updated = stores.toMutableList()
        val index = updated.indexOfFirst { it.id == definition.id }
        if (index >= 0) {
            updated[index] = definition
        } else {
            updated.add(definition)
        }
        stores = updated.sortedBy { it.name.lowercase() }
        repository.save(stores)
    }

    fun removeStore(definition: StoreDefinition) {
        stores = stores.filterNot { it.id == definition.id }
        repository.save(stores)
    }
}
