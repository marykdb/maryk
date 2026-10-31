package maryk.datastore.shared.updates

import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import maryk.core.models.key
import maryk.core.query.ValuesWithMetaData
import maryk.core.query.changes.Change
import maryk.core.query.changes.DataObjectVersionedChange
import maryk.core.query.changes.ListChange
import maryk.core.query.changes.change
import maryk.core.query.pairs.with
import maryk.core.query.requests.get
import maryk.core.query.requests.getChanges
import maryk.core.query.requests.getUpdates
import maryk.core.query.responses.ValuesResponse
import maryk.core.query.responses.ChangesResponse
import maryk.core.query.responses.UpdatesResponse
import maryk.core.query.responses.updates.ChangeUpdate
import maryk.core.query.responses.updates.AdditionUpdate
import maryk.core.query.responses.updates.InitialValuesUpdate
import maryk.core.query.responses.updates.OrderedKeysUpdate
import maryk.test.models.SimpleMarykModel
import maryk.test.models.TestMarykModel
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.time.Duration.Companion.milliseconds

class UpdateListenerForGetTest {
    @Test
    fun repeatedOccurrencesWorkForValuesChangesAndUpdates() = runTest {
        val model = SimpleMarykModel
        val key = model.key(ByteArray(model.Meta.keyByteSize) { 1 })
        val other = model.key(ByteArray(model.Meta.keyByteSize) { 2 })
        val keys = listOf(key, other, key)
        val values = model.create { value with "initial" }
        val listeners = listOf<UpdateListener<SimpleMarykModel, *>>(
            UpdateListenerForGet(model.get(*keys.toTypedArray()), ValuesResponse(model,
                keys.map { ValuesWithMetaData(it, values, 1uL, 1uL, false) })),
            UpdateListenerForGet(model.getUpdates(*keys.toTypedArray()), UpdatesResponse(model,
                listOf(OrderedKeysUpdate(keys = keys, version = 1uL)))),
            UpdateListenerForGet(model.getChanges(*keys.toTypedArray()), ChangesResponse(model,
                keys.map { DataObjectVersionedChange(key = it, changes = emptyList()) })),
        )
        try {
            for (listener in listeners) {
                listener.process(Update.Change(model, key, 2uL, listOf(Change(model.ref { value } with "changed"))),
                    ProcessUpdateActorTest.TestDataStore)
                val changes = listener.getFlow().take(3).toList().filterIsInstance<ChangeUpdate<SimpleMarykModel>>()
                assertEquals(listOf(0, 2), changes.map { it.index })
                listener.process(Update.Deletion(model, key, 3uL, true), ProcessUpdateActorTest.TestDataStore)
                assertEquals(listOf(other), listener.matchingKeys.value)
                listener.process(Update.Addition(model, key, 4uL, values), ProcessUpdateActorTest.TestDataStore)
                assertEquals(keys, listener.matchingKeys.value)
                val additions = listener.getFlow().take(4).toList().filterIsInstance<AdditionUpdate<SimpleMarykModel>>()
                assertEquals(listOf(0, 2), additions.map { it.insertionIndex })
            }
        } finally {
            listeners.forEach { it.close() }
        }
    }

    @Test
    fun additionPayloadsAreOwnedByEachListener() = runTest {
        val model = TestMarykModel
        val key = model.key(ByteArray(model.Meta.keyByteSize))
        val request = model.get(key)
        val response = ValuesResponse(model, emptyList())
        val first = UpdateListenerForGet(request, response)
        val second = UpdateListenerForGet(request, response)
        val update = Update.Addition(model, key, 1uL, model.create { listOfString with listOf("original") })
        try {
            first.process(update, ProcessUpdateActorTest.TestDataStore)
            second.process(update, ProcessUpdateActorTest.TestDataStore)
            val firstEvents = first.getFlow().take(2).toList()
            val firstAddition = assertIs<AdditionUpdate<TestMarykModel>>(firstEvents.last())
            (firstAddition.values { listOfString } as MutableList<*>).clear()
            val secondAddition = assertIs<AdditionUpdate<TestMarykModel>>(second.getFlow().take(2).toList().last())
            assertEquals(listOf("original"), secondAddition.values { listOfString })
            assertEquals(listOf("original"), update.values { listOfString })
        } finally {
            first.close()
            second.close()
        }
    }

    @Test
    fun changePayloadsAreOwnedByEachListener() = runTest {
        val model = TestMarykModel
        val key = model.key(ByteArray(model.Meta.keyByteSize))
        val request = model.get(key)
        val initial = model.create { listOfString with listOf("initial") }
        val response = ValuesResponse(model, listOf(ValuesWithMetaData(key, initial, 1uL, 1uL, false)))
        val first = UpdateListenerForGet(request, response)
        val second = UpdateListenerForGet(request, response)
        val update = Update.Change(model, key, 2uL, listOf(
            ListChange(model.ref { listOfString }.change(addValuesToEnd = listOf("appended"))),
        ))
        try {
            first.process(update, ProcessUpdateActorTest.TestDataStore)
            second.process(update, ProcessUpdateActorTest.TestDataStore)
            val firstChange = assertIs<ChangeUpdate<TestMarykModel>>(first.getFlow().take(2).toList().last())
            val values = assertIs<ListChange>(firstChange.changes.single()).listValueChanges.single().addValuesToEnd
            (values as MutableList<*>).clear()
            val secondChange = assertIs<ChangeUpdate<TestMarykModel>>(second.getFlow().take(2).toList().last())
            val secondValues = assertIs<ListChange>(secondChange.changes.single()).listValueChanges.single().addValuesToEnd
            assertEquals(listOf("appended"), secondValues)
        } finally {
            first.close()
            second.close()
        }
    }

    @Test
    fun requestedKeyCanBeAddedAfterMissingInitialResponseAndReaddedAfterDeletion() = runTest {
        val key = SimpleMarykModel.key(ByteArray(16) { 1 })
        val values = SimpleMarykModel.create {
            value with "present"
        }
        val listener = UpdateListenerForGet(
            SimpleMarykModel.get(key),
            ValuesResponse(dataModel = SimpleMarykModel, values = emptyList())
        )

        listener.process(Update.Addition(SimpleMarykModel, key, 1uL, values), ProcessUpdateActorTest.TestDataStore)

        assertEquals(listOf(key), listener.matchingKeys.value)

        listener.process(
            Update.Deletion(SimpleMarykModel, key, 2uL, isHardDelete = true),
            ProcessUpdateActorTest.TestDataStore
        )
        assertEquals(emptyList(), listener.matchingKeys.value)

        listener.process(Update.Addition(SimpleMarykModel, key, 3uL, values), ProcessUpdateActorTest.TestDataStore)
        assertEquals(listOf(key), listener.matchingKeys.value)
    }

    @Test
    fun addValuesRespectsRequestKeyOrderWhenUnsorted() {
        val firstKey = SimpleMarykModel.key(ByteArray(16) { 2 })
        val secondKey = SimpleMarykModel.key(ByteArray(16) { 1 })
        val request = SimpleMarykModel.get(firstKey, secondKey)

        val values = SimpleMarykModel.create {
            value with "value"
        }

        val response = ValuesResponse(
            dataModel = SimpleMarykModel,
            values = listOf(
                ValuesWithMetaData(
                    key = firstKey,
                    values = values,
                    firstVersion = 1uL,
                    lastVersion = 1uL,
                    isDeleted = false,
                ),
                ValuesWithMetaData(
                    key = secondKey,
                    values = values,
                    firstVersion = 1uL,
                    lastVersion = 1uL,
                    isDeleted = false,
                ),
            ),
        )

        val listener = UpdateListenerForGet(request, response)

        assertEquals(0, listener.addValues(firstKey, values))
        assertEquals(1, listener.addValues(secondKey, values))
    }

    @Test
    fun addValuesReturnsNullForUnknownKey() {
        val key = SimpleMarykModel.key(ByteArray(16) { 3 })
        val request = SimpleMarykModel.get(key)
        val values = SimpleMarykModel.create {
            value with "value"
        }
        val response = ValuesResponse(
            dataModel = SimpleMarykModel,
            values = listOf(
                ValuesWithMetaData(
                    key = key,
                    values = values,
                    firstVersion = 1uL,
                    lastVersion = 1uL,
                    isDeleted = false,
                ),
            ),
        )
        val listener = UpdateListenerForGet(request, response)
        val missingKey = SimpleMarykModel.key(ByteArray(16) { 4 })

        assertNull(listener.addValues(missingKey, values))
    }

    @Test
    fun requestedKeyIndexCachesMembershipAndInsertionOrder() {
        val first = SimpleMarykModel.key(ByteArray(16) { 1 })
        val second = SimpleMarykModel.key(ByteArray(16) { 2 })
        val third = SimpleMarykModel.key(ByteArray(16) { 3 })
        val index = OrderedKeyIndex(listOf(first, second, third), listOf(first, third))

        assertEquals(true, index.contains(second))
        assertEquals(false, index.contains(SimpleMarykModel.key(ByteArray(16) { 4 })))
        assertEquals(listOf(first, third), index.orderedPresentKeys())
        index.add(second)
        assertEquals(true, index.isPresent(second))
        assertEquals(listOf(first, second, third), index.orderedPresentKeys())
        index.remove(second)
        assertEquals(false, index.isPresent(second))
    }

    @Test
    fun updateProcessedBeforeCollectionIsBuffered() = runTest {
        val key = SimpleMarykModel.key(ByteArray(16))
        val initialValues = SimpleMarykModel.create {
            value with "initial"
        }
        val request = SimpleMarykModel.get(key)
        val response = ValuesResponse(
            dataModel = SimpleMarykModel,
            values = listOf(
                ValuesWithMetaData(
                    key = key,
                    values = initialValues,
                    firstVersion = 1uL,
                    lastVersion = 1uL,
                    isDeleted = false,
                ),
            ),
        )
        val listener = UpdateListenerForGet(request, response)
        val change = Change(SimpleMarykModel.ref { value } with "changed")

        listener.process(
            Update.Change(SimpleMarykModel, key, 2uL, listOf(change)),
            ProcessUpdateActorTest.TestDataStore
        )

        val updates = withTimeout(200.milliseconds) {
            listener.getFlow().take(2).toList()
        }

        assertIs<InitialValuesUpdate<*>>(updates[0])
        assertIs<ChangeUpdate<*>>(updates[1]).apply {
            assertEquals(key, this.key)
            assertEquals(listOf(change), changes)
        }
    }
}
