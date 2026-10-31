package maryk.datastore.test

import maryk.core.models.key
import maryk.core.query.changes.Change
import maryk.core.query.changes.IsChange
import maryk.core.query.changes.ListChange
import maryk.core.query.changes.change
import maryk.core.query.pairs.with
import maryk.core.query.requests.add
import maryk.core.query.requests.change
import maryk.core.query.requests.get
import maryk.core.query.responses.statuses.AddSuccess
import maryk.core.query.responses.statuses.ChangeSuccess
import maryk.core.values.Values
import maryk.datastore.shared.IsDataStore
import kotlin.test.assertEquals
import kotlin.test.assertIs

/** Compare storage with literal collection edits at byte-length and list-index boundaries. */
suspend fun assertCollectionBoundaryIntegrity(store: IsDataStore) {
    val model = StatefulIntegrityRecord
    val key = model.key(ByteArray(model.Meta.keyByteSize))
    var numbers = (0..260).toList()
    fun detail(id: Int) = StatefulIntegrityDetails.create {
        note with "row-$id"
        this.numbers with listOf(id, id + 1)
        attributes with mapOf("" to "$id", "long".repeat(40) to "\u0000🚀")
    }
    var boxes = (0..260).map(::detail)
    var entries = listOf(0, 1, 127, 128, 129, 255, 256).associate {
        "a".repeat(it) + "\u0000🚀" to detail(it)
    }
    fun image() = model.create {
        label with "boundary"
        token with "boundary-token"
        items with numbers
        this.boxes with boxes
        this.entries with entries
    }
    val history = mutableListOf<Pair<ULong, Values<StatefulIntegrityRecord>>>()
    suspend fun verify(version: ULong, context: String) {
        val expected = image()
        assertEquals(expected, store.execute(model.get(key)).values.single().values, "Current: $context")
        history.add(version to expected)
    }
    suspend fun mutate(context: String, vararg changes: IsChange) {
        val status = assertIs<ChangeSuccess<StatefulIntegrityRecord>>(
            store.execute(model.change(key.change(*changes))).statuses.single(),
        )
        verify(status.version, context)
    }
    val added = assertIs<AddSuccess<StatefulIntegrityRecord>>(store.execute(model.add(key to image())).statuses.single())
    verify(added.version, "creation")
    for (index in listOf(128, 0, 255, 127)) {
        numbers = numbers.toMutableList().also { it.removeAt(index) }
        boxes = boxes.toMutableList().also { it.removeAt(index) }
        mutate("delete-$index",
            Change(model.ref { items at index.toUInt() } with null),
            Change(model.ref { this.boxes at index.toUInt() } with null),
        )
    }
    val inserts = linkedMapOf(128u to -128, 0u to -1, 256u to -256, 127u to -127)
    numbers = numbers.toMutableList().also { list ->
        for ((index, value) in inserts) list.add(index.toInt(), value)
    }
    boxes = boxes.toMutableList().also { list ->
        for ((index, value) in inserts) list.add(index.toInt(), detail(value))
    }
    mutate("mixed-inserts",
        ListChange(model.ref { items }.change(addValuesAtIndex = inserts)),
        ListChange(model.ref { this.boxes }.change(addValuesAtIndex = inserts.mapValues { detail(it.value) })),
    )
    for (size in listOf(256, 255, 129, 128, 127, 1, 0, 128, 257)) {
        numbers = List(size) { it + size }
        boxes = List(size) { detail(it + size) }
        mutate("replace-$size", Change(model.ref { items } with numbers, model.ref { this.boxes } with boxes))
    }
    for (mapKey in entries.keys.toList()) {
        entries = entries - mapKey
        mutate("map-delete-${mapKey.length}", Change(model.ref { this.entries at mapKey } with null))
    }
    for ((version, snapshot) in history) {
        assertEquals(snapshot, store.execute(model.get(key, toVersion = version)).values.single().values,
            "History at $version after all mutations")
    }

}
