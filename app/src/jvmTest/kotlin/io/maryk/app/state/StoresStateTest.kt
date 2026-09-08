package io.maryk.app.state

import io.maryk.app.config.StoreDefinition
import io.maryk.app.config.StoreKind
import io.maryk.app.config.StoreRepository
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class StoresStateTest {
    @Test
    fun failedSaveDoesNotPublishAnUnsavedStore() {
        val state = StoresState(FailingStoreRepository())

        assertFailsWith<IllegalStateException> {
            state.upsertStore(store)
        }

        assertEquals(emptyList(), state.stores)
    }

    @Test
    fun failedRemovalDoesNotHideTheSavedStore() {
        val repository = RecordingStoreRepository()
        val state = StoresState(repository)
        state.upsertStore(store)
        repository.failSaves = true
        state.requestStoreRemoval(store)

        assertFailsWith<IllegalStateException> {
            state.confirmStoreRemoval()
        }

        assertEquals(listOf(store), state.stores)
    }
}

private val store = StoreDefinition(
    id = "store-id",
    name = "Store",
    type = StoreKind.ROCKS_DB,
    directory = "/tmp/store",
)

private open class RecordingStoreRepository : StoreRepository() {
    var failSaves = false

    override fun save(stores: List<StoreDefinition>) {
        check(!failSaves) { "save failed" }
    }
}

private class FailingStoreRepository : RecordingStoreRepository() {
    init {
        failSaves = true
    }
}
