package maryk.datastore.test

import maryk.core.models.DataModel
import maryk.core.models.RootDataModel
import maryk.core.models.key
import maryk.core.properties.definitions.EmbeddedValuesDefinition
import maryk.core.properties.definitions.MultiTypeDefinition
import maryk.core.properties.definitions.NumberDefinition
import maryk.core.properties.definitions.StringDefinition
import maryk.core.properties.definitions.embed
import maryk.core.properties.definitions.incrementingMap
import maryk.core.properties.definitions.list
import maryk.core.properties.definitions.map
import maryk.core.properties.definitions.multiType
import maryk.core.properties.definitions.set
import maryk.core.properties.definitions.string
import maryk.core.properties.types.TypedValue
import maryk.core.properties.types.invoke
import maryk.core.properties.types.numeric.SInt32
import maryk.core.properties.types.numeric.UInt32
import maryk.core.query.changes.Change
import maryk.core.query.changes.Check
import maryk.core.query.changes.IncMapChange
import maryk.core.query.changes.IsChange
import maryk.core.query.changes.ListChange
import maryk.core.query.changes.ObjectSoftDeleteChange
import maryk.core.query.changes.SetChange
import maryk.core.query.changes.change
import maryk.core.query.filters.Equals
import maryk.core.query.pairs.with
import maryk.core.query.requests.add
import maryk.core.query.requests.change
import maryk.core.query.requests.delete
import maryk.core.query.requests.get
import maryk.core.query.requests.scan
import maryk.core.query.responses.statuses.AddSuccess
import maryk.core.query.responses.statuses.ChangeSuccess
import maryk.core.query.responses.statuses.DeleteSuccess
import maryk.core.query.responses.statuses.ValidationFail
import maryk.core.values.Values
import maryk.datastore.shared.IsDataStore
import maryk.datastore.shared.backup
import maryk.datastore.shared.restore
import maryk.test.models.EmbeddedMarykModel
import maryk.test.models.MarykTypeEnum
import kotlin.random.Random
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

object StatefulIntegrityDetails : DataModel<StatefulIntegrityDetails>() {
    val note by string(1u, required = false)
    val numbers by list(2u, valueDefinition = NumberDefinition(type = SInt32), required = false)
    val attributes by map(3u, keyDefinition = StringDefinition(), valueDefinition = StringDefinition(), required = false)
}

object StatefulIntegrityRecord : RootDataModel<StatefulIntegrityRecord>(indexes = { listOf(StatefulIntegrityRecord.label.ref()) }) {
    val label by string(1u)
    val token by string(2u, unique = true)
    val details by embed(3u, dataModel = { StatefulIntegrityDetails }, required = false)
    val items by list(4u, valueDefinition = NumberDefinition(type = SInt32), required = false)
    val boxes by list(5u, valueDefinition = EmbeddedValuesDefinition(dataModel = { StatefulIntegrityDetails }), required = false)
    val entries by map(6u, keyDefinition = StringDefinition(), valueDefinition = EmbeddedValuesDefinition(dataModel = { StatefulIntegrityDetails }), required = false)
    val tags by set(7u, valueDefinition = StringDefinition(), required = false)
    val allocated by incrementingMap(8u, keyNumberDescriptor = UInt32,
        valueDefinition = EmbeddedValuesDefinition(dataModel = { StatefulIntegrityDetails }), required = false)
    val typed by multiType(9u, typeEnum = MarykTypeEnum, required = false, typeIsFinal = false)
    val typedBoxes by list(10u, valueDefinition = MultiTypeDefinition(typeEnum = MarykTypeEnum, typeIsFinal = false), required = false)
}

/** Generated commands are checked against plain immutable images, never Values.change. */
suspend fun assertStatefulIntegrity(
    open: suspend (String) -> IsDataStore,
    supportsReopen: Boolean,
) {
    for (seed in listOf(17, 343, 20261028)) {
        val sourceName = "source-$seed"
        var source = open(sourceName)
        val target = open("target-$seed")
        try {
            val random = Random(seed)
            fun key(id: Int) = StatefulIntegrityRecord.key(ByteArray(StatefulIntegrityRecord.Meta.keyByteSize).also { it[it.lastIndex] = id.toByte() })
            val subject = key(1)
            val blocker = key(2)
            var image = StateImage("subject-$seed", "subject-token-$seed")
            var deleted = false
            var maximum = 0u
            val history = mutableListOf<StateSnapshot>()
            val trace = mutableListOf<String>()
            val added = assertIs<AddSuccess<StatefulIntegrityRecord>>(source.execute(StatefulIntegrityRecord.add(subject to image.values())).statuses.single())
            history += StateSnapshot(added.version, image, false)
            val blocked = assertIs<AddSuccess<StatefulIntegrityRecord>>(source.execute(StatefulIntegrityRecord.add(
                blocker to StateImage("blocker-$seed", "reserved-$seed").values(),
            )).statuses.single())

            fun text() = listOf("", "plain", "a.b", "a\\b", "\u0000🙂")[random.nextInt(5)]
            fun numbers() = List(random.nextInt(4)) { random.nextInt(-3, 4) }
            fun detail() = StateDetail(
                note = text().takeIf { random.nextBoolean() },
                numbers = numbers().takeIf { random.nextBoolean() },
                attributes = (if (random.nextBoolean()) emptyMap() else mapOf(text() to text())).takeIf { random.nextBoolean() },
            )
            suspend fun check(store: IsDataStore, snapshot: StateSnapshot, historic: Boolean) {
                val actual = store.execute(StatefulIntegrityRecord.get(subject, filterSoftDeleted = false,
                    toVersion = snapshot.version.takeIf { historic })).values.single()
                val context = "seed=$seed historic=$historic version=${snapshot.version} trace=${trace.joinToString(";")}"
                assertEquals(snapshot.image.values(), actual.values, context)
                assertEquals(snapshot.deleted, actual.isDeleted, context)
                assertEquals(added.version, actual.firstVersion, context)
            }
            suspend fun mutate(description: String, next: StateImage, nextDeleted: Boolean = deleted, changes: List<IsChange>) {
                trace += description
                val response = source.execute(StatefulIntegrityRecord.change(
                    subject.change(*changes.toTypedArray()),
                )).statuses.single()
                val result = assertIs<ChangeSuccess<StatefulIntegrityRecord>>(response, "seed=$seed $response ${trace.joinToString(";")}")
                assertTrue(result.version >= history.last().version, "version regressed, seed=$seed")
                image = next
                deleted = nextDeleted
                if (result.version != history.last().version) history += StateSnapshot(result.version, image, deleted)
                else assertEquals(StateSnapshot(result.version, image, deleted), history.last(), "changed image without version, seed=$seed")
                check(source, history.last(), false)
            }
            suspend fun allocate(value: StateDetail, description: String) {
                val nextKey = maximum + 1u
                val changes = mutableListOf<IsChange>()
                if (image.allocated == null) changes += Change(StatefulIntegrityRecord.ref { allocated } with emptyMap<UInt, Values<StatefulIntegrityDetails>>())
                changes += IncMapChange(StatefulIntegrityRecord.ref { allocated }.change(addValues = listOf(value.values())))
                mutate(description, image.copy(allocated = image.allocated.orEmpty() + (nextKey to value)), changes = changes)
                maximum = nextKey
            }

            // Every command family is visited once per seed, then mixed in a different order.
            val commands = (0..29).shuffled(random) + List(30) { random.nextInt(30) }
            for ((step, command) in commands.withIndex()) {
                when (command) {
                    0 -> {
                        val value = numbers().takeIf { random.nextBoolean() }
                        mutate("items=$value", image.copy(items = value), changes = listOf(Change(StatefulIntegrityRecord.ref { items } with value)))
                    }
                    1 -> {
                        val items = image.items
                        if (items.isNullOrEmpty()) {
                            val value = numbers()
                            mutate("items-init", image.copy(items = value), changes = listOf(Change(StatefulIntegrityRecord.ref { this.items } with value)))
                        } else {
                            val index = random.nextInt(items.size)
                            val value = random.nextInt(-9, 10)
                            val next = items.toMutableList().also { it[index] = value }.toList()
                            mutate("item-$index=$value", image.copy(items = next), changes = listOf(Change(StatefulIntegrityRecord.ref { this.items at index.toUInt() } with value)))
                        }
                    }
                    2 -> {
                        val items = image.items
                        if (items.isNullOrEmpty()) {
                            mutate("items-empty", image.copy(items = emptyList()), changes = listOf(Change(StatefulIntegrityRecord.ref { this.items } with emptyList<Int>())))
                        } else {
                            val index = random.nextInt(items.size)
                            mutate("item-$index-delete", image.copy(items = items.filterIndexed { i, _ -> i != index }), changes = listOf(Change(StatefulIntegrityRecord.ref { this.items at index.toUInt() } with null)))
                        }
                    }
                    3 -> {
                        val boxes = List(random.nextInt(4)) { detail() }
                        mutate("boxes-replace", image.copy(boxes = boxes), changes = listOf(Change(StatefulIntegrityRecord.ref { this.boxes } with boxes.map { it.values() })))
                    }
                    4 -> {
                        val boxes = image.boxes
                        if (boxes.isNullOrEmpty()) {
                            val next = listOf(detail(), detail())
                            mutate("boxes-init", image.copy(boxes = next), changes = listOf(Change(StatefulIntegrityRecord.ref { this.boxes } with next.map { it.values() })))
                        } else {
                            val index = random.nextInt(boxes.size)
                            val note = text().takeIf { random.nextBoolean() }
                            val next = boxes.mapIndexed { i, box -> if (i == index) box.copy(note = note) else box }
                            mutate("box-$index-note", image.copy(boxes = next), changes = listOf(Change(StatefulIntegrityDetails.ref(StatefulIntegrityRecord.ref { this.boxes at index.toUInt() }) { this.note } with note)))
                        }
                    }
                    5 -> {
                        val values = if (random.nextBoolean()) emptyMap() else mapOf(text() to detail(), "retained" to detail())
                        mutate("entries-replace", image.copy(entries = values), changes = listOf(Change(StatefulIntegrityRecord.ref { entries } with values.mapValues { it.value.values() })))
                    }
                    6 -> {
                        val key = text()
                        val value = detail()
                        mutate("entry-$key-set", image.copy(entries = image.entries.orEmpty() + (key to value)), changes = listOf(Change(StatefulIntegrityRecord.ref { entries at key } with value.values())))
                    }
                    7 -> {
                        val key = image.entries?.keys?.firstOrNull() ?: "missing"
                        val next = image.entries?.filterKeys { it != key }
                        mutate("entry-$key-delete", image.copy(entries = next), changes = listOf(Change(StatefulIntegrityRecord.ref { entries at key } with null)))
                    }
                    8 -> {
                        val value = detail().takeIf { random.nextBoolean() }
                        mutate("detail-replace", image.copy(details = value), changes = listOf(Change(StatefulIntegrityRecord.ref { details } with value?.values())))
                    }
                    9 -> {
                        val old = image.details
                        if (old == null) {
                            val next = detail()
                            mutate("detail-init", image.copy(details = next), changes = listOf(Change(StatefulIntegrityRecord.ref { details } with next.values())))
                        } else {
                            val note = text().takeIf { random.nextBoolean() }
                            mutate("detail-note", image.copy(details = old.copy(note = note)), changes = listOf(Change(StatefulIntegrityRecord.ref { details { this.note } } with note)))
                        }
                    }
                    10 -> {
                        val value = when (random.nextInt(3)) {
                            0 -> StateTyped(0, text().ifEmpty { "nonempty" }, emptyList())
                            1 -> StateTyped(1, text(), emptyList())
                            else -> StateTyped(2, null, List(random.nextInt(3)) { text().ifEmpty { "nonempty" } })
                        }.takeIf { random.nextInt(4) != 0 }
                        mutate("typed-replace-$value", image.copy(typed = value), changes = listOf(Change(StatefulIntegrityRecord.ref { typed } with value?.value())))
                    }
                    11 -> {
                        val value = StateTyped(1, text(), emptyList())
                        mutate("typed-embed", image.copy(typed = value), changes = listOf(Change(StatefulIntegrityRecord.ref { typed } with value.value())))
                    }
                    12 -> {
                        val values = (if (random.nextBoolean()) emptySet() else setOf(text(), text())).takeIf { random.nextBoolean() }
                        mutate("tags-replace", image.copy(tags = values), changes = listOf(Change(StatefulIntegrityRecord.ref { tags } with values)))
                    }
                    13 -> {
                        val key = random.nextInt(1, 51).toUInt()
                        val value = detail()
                        val values = if (random.nextBoolean()) emptyMap() else mapOf(key to value)
                        mutate("allocated-replace-$key", image.copy(allocated = values), changes = listOf(Change(StatefulIntegrityRecord.ref { allocated } with values.mapValues { it.value.values() })))
                        maximum = maxOf(maximum, values.keys.maxOrNull() ?: 0u)
                    }
                    14 -> allocate(detail(), "allocate")
                    15 -> {
                        val key = image.allocated?.keys?.maxOrNull() ?: maximum
                        val next = image.allocated?.filterKeys { it != key }
                        mutate("allocated-$key-delete", image.copy(allocated = next), changes = listOf(Change(StatefulIntegrityRecord.ref { allocated at key } with null)))
                    }
                    16 -> mutate("allocated-remove", image.copy(allocated = null), changes = listOf(Change(StatefulIntegrityRecord.ref { allocated } with null)))
                    17 -> {
                        if (deleted) mutate("undelete", image, false, listOf(ObjectSoftDeleteChange(false)))
                        else {
                            trace += "soft-delete"
                            val result = assertIs<DeleteSuccess<StatefulIntegrityRecord>>(source.execute(StatefulIntegrityRecord.delete(subject)).statuses.single())
                            deleted = true
                            history += StateSnapshot(result.version, image, true)
                            check(source, history.last(), false)
                        }
                    }
                    18 -> {
                        trace += "rejected-unique-with-allocation"
                        val before = history.last()
                        val changes = mutableListOf<IsChange>(Change(StatefulIntegrityRecord.ref { items } with listOf(991, 992)))
                        if (image.allocated == null) changes += Change(StatefulIntegrityRecord.ref { allocated } with emptyMap<UInt, Values<StatefulIntegrityDetails>>())
                        changes += IncMapChange(StatefulIntegrityRecord.ref { allocated }.change(addValues = listOf(StateDetail("must rollback").values())))
                        changes += Change(StatefulIntegrityRecord.ref { token } with "reserved-$seed")
                        changes += ObjectSoftDeleteChange(false)
                        val rejected = source.execute(StatefulIntegrityRecord.change(subject.change(*changes.toTypedArray()))).statuses.single()
                        assertIs<ValidationFail<StatefulIntegrityRecord>>(rejected, "seed=$seed response=$rejected trace=$trace")
                        check(source, before, false)
                        assertEquals(before.version, source.execute(StatefulIntegrityRecord.get(subject, filterSoftDeleted = false)).values.single().lastVersion)
                        allocate(detail(), "allocate-after-rejected-request")
                    }
                    19 -> {
                        trace += "rejected-check-after-items"
                        val before = history.last()
                        assertIs<ValidationFail<StatefulIntegrityRecord>>(source.execute(StatefulIntegrityRecord.change(subject.change(
                            Change(StatefulIntegrityRecord.ref { items } with listOf(881, 882)),
                            Check(StatefulIntegrityRecord.ref { label } with "incorrect-$seed"),
                        ))).statuses.single())
                        check(source, before, false)
                        assertEquals(before.version, source.execute(StatefulIntegrityRecord.get(subject, filterSoftDeleted = false)).values.single().lastVersion)
                    }
                    20 -> {
                        val items = image.items.orEmpty()
                        val value = random.nextInt(-9, 10)
                        val index = random.nextInt(items.size + 1)
                        val changes = mutableListOf<IsChange>()
                        if (image.items == null) changes += Change(StatefulIntegrityRecord.ref { this.items } with emptyList<Int>())
                        changes += ListChange(StatefulIntegrityRecord.ref { this.items }.change(addValuesAtIndex = mapOf(index.toUInt() to value)))
                        val next = items.toMutableList().also { it.add(index, value) }.toList()
                        mutate("list-insert-$index", image.copy(items = next), changes = changes)
                    }
                    21 -> {
                        val value = text()
                        val changes = mutableListOf<IsChange>()
                        if (image.tags == null) changes += Change(StatefulIntegrityRecord.ref { tags } with emptySet<String>())
                        changes += SetChange(StatefulIntegrityRecord.ref { tags }.change(addValues = setOf(value)))
                        mutate("set-add-$value", image.copy(tags = image.tags.orEmpty() + value), changes = changes)
                    }
                    22 -> {
                        val next = detail()
                        val note = text()
                        mutate("parent-and-child-one-version", image.copy(details = next.copy(note = note)), changes = listOf(
                            Change(StatefulIntegrityRecord.ref { details } with next.values()),
                            Change(StatefulIntegrityRecord.ref { details { this.note } } with note),
                        ))
                    }
                    23 -> mutate("clear-multiple", image.copy(items = emptyList(), boxes = emptyList(), entries = emptyMap(), tags = emptySet()), changes = listOf(Change(
                        StatefulIntegrityRecord.ref { items } with emptyList<Int>(),
                        StatefulIntegrityRecord.ref { boxes } with emptyList<Values<StatefulIntegrityDetails>>(),
                        StatefulIntegrityRecord.ref { entries } with emptyMap<String, Values<StatefulIntegrityDetails>>(),
                        StatefulIntegrityRecord.ref { tags } with emptySet<String>(),
                    )))
                    24 -> {
                        if (image.boxes.isNullOrEmpty()) {
                            val initial = List(3) { detail() }
                            mutate("boxes-before-delete", image.copy(boxes = initial), changes = listOf(Change(StatefulIntegrityRecord.ref { boxes } with initial.map(StateDetail::values))))
                        }
                        val original = image.boxes!!
                        val index = random.nextInt(original.size)
                        mutate("box-$index-delete", image.copy(boxes = original.filterIndexed { i, _ -> i != index }),
                            changes = listOf(Change(StatefulIntegrityRecord.ref { boxes at index.toUInt() } with null)))
                    }
                    25 -> {
                        val original = image.boxes.orEmpty()
                        val removed = original.firstOrNull().takeIf { random.nextBoolean() }
                        val next = original.toMutableList().also { if (removed != null) it.remove(removed) }
                        val index = random.nextInt(next.size + 1)
                        val value = detail()
                        next.add(index, value)
                        val changes = mutableListOf<IsChange>()
                        if (image.boxes == null) changes += Change(StatefulIntegrityRecord.ref { boxes } with emptyList<Values<StatefulIntegrityDetails>>())
                        changes += ListChange(StatefulIntegrityRecord.ref { boxes }.change(
                            deleteValues = removed?.let { listOf(it.values()) }, addValuesAtIndex = mapOf(index.toUInt() to value.values())))
                        mutate("boxes-insert-$index", image.copy(boxes = next.toList()), changes = changes)
                    }
                    26 -> {
                        val values = listOf(StateTyped(0, "simple", emptyList()), StateTyped(1, text(), emptyList()), StateTyped(2, null, listOf("list", "items")))
                        mutate("typed-boxes-replace", image.copy(typedBoxes = values), changes = listOf(Change(StatefulIntegrityRecord.ref { typedBoxes } with values.map(StateTyped::value))))
                    }
                    27 -> {
                        if (image.typedBoxes.isNullOrEmpty()) {
                            val initial = listOf(StateTyped(1, "first", emptyList()), StateTyped(2, null, listOf("second")), StateTyped(0, "last", emptyList()))
                            mutate("typed-boxes-before-delete", image.copy(typedBoxes = initial), changes = listOf(Change(StatefulIntegrityRecord.ref { typedBoxes } with initial.map(StateTyped::value))))
                        }
                        val original = image.typedBoxes!!
                        val index = random.nextInt(original.size)
                        mutate("typed-box-$index-delete", image.copy(typedBoxes = original.filterIndexed { i, _ -> i != index }),
                            changes = listOf(Change(StatefulIntegrityRecord.ref { typedBoxes at index.toUInt() } with null)))
                    }
                    28 -> {
                        val original = image.typedBoxes.orEmpty()
                        val value = StateTyped(1, text(), emptyList())
                        val index = random.nextInt(original.size + 1)
                        val next = original.toMutableList().also { it.add(index, value) }.toList()
                        val changes = mutableListOf<IsChange>()
                        if (image.typedBoxes == null) changes += Change(StatefulIntegrityRecord.ref { typedBoxes } with emptyList<TypedValue<MarykTypeEnum<out Any>, Any>>())
                        changes += ListChange(StatefulIntegrityRecord.ref { typedBoxes }.change(addValuesAtIndex = mapOf(index.toUInt() to value.value())))
                        mutate("typed-boxes-insert-$index", image.copy(typedBoxes = next), changes = changes)
                    }
                    29 -> {
                        mutate("duplicate-items-init", image.copy(items = listOf(3, 4, 3, 3)), changes = listOf(Change(StatefulIntegrityRecord.ref { items } with listOf(3, 4, 3, 3))))
                        mutate("duplicate-item-delete-one", image.copy(items = listOf(4, 9, 3, 3)), changes = listOf(ListChange(StatefulIntegrityRecord.ref { items }.change(
                            deleteValues = listOf(3), addValuesAtIndex = mapOf(1u to 9),
                        ))))
                        mutate("duplicate-item-delete-two", image.copy(items = listOf(4, 9)), changes = listOf(ListChange(StatefulIntegrityRecord.ref { items }.change(deleteValues = listOf(3, 3, 999)))))
                        val repeated = StateDetail("repeated", numbers = listOf(1, 2))
                        val kept = StateDetail("kept", attributes = mapOf("kept" to "value"))
                        val inserted = StateDetail("inserted")
                        val initial = listOf(repeated, kept, repeated, repeated)
                        mutate("duplicate-boxes-init", image.copy(boxes = initial), changes = listOf(Change(StatefulIntegrityRecord.ref { boxes } with initial.map(StateDetail::values))))
                        mutate("duplicate-box-delete-one", image.copy(boxes = listOf(kept, inserted, repeated, repeated)), changes = listOf(ListChange(StatefulIntegrityRecord.ref { boxes }.change(
                            deleteValues = listOf(repeated.values()), addValuesAtIndex = mapOf(1u to inserted.values()),
                        ))))
                        mutate("duplicate-box-delete-two", image.copy(boxes = listOf(kept, inserted)), changes = listOf(ListChange(StatefulIntegrityRecord.ref { boxes }.change(
                            deleteValues = listOf(repeated.values(), repeated.values(), StateDetail("missing").values()),
                        ))))
                    }
                }
                if (step % 8 == 7) {
                    for (snapshot in listOf(history.first(), history[history.size / 2], history.last()).distinct()) check(source, snapshot, true)
                    assertEquals(if (deleted) emptyList() else listOf(subject), source.execute(StatefulIntegrityRecord.scan(
                        where = Equals(StatefulIntegrityRecord.ref { token } with image.token),
                    )).values.map { it.key }, "unique lookup, seed=$seed")
                }
                if (supportsReopen && step == 23) {
                    source.close()
                    source = open(sourceName)
                    check(source, history.last(), false)
                }
            }
            for (snapshot in history) check(source, snapshot, true)
            val backup = SerializedBackup(source)
            source.backup(backup, snapshotVersion = maxOf(history.last().version, blocked.version) + 1uL, batchSize = 1u)
            assertEquals(2uL, target.restore(backup).records, "seed=$seed")
            for (snapshot in history) check(target, snapshot, true)
            check(target, history.last(), false)
            val expectedNext = maximum + 1u
            for (store in listOf(source, target)) {
                val changes = mutableListOf<IsChange>()
                if (image.allocated == null) changes += Change(StatefulIntegrityRecord.ref { allocated } with emptyMap<UInt, Values<StatefulIntegrityDetails>>())
                changes += IncMapChange(StatefulIntegrityRecord.ref { allocated }.change(addValues = listOf(StateDetail("after backup").values())))
                assertIs<ChangeSuccess<StatefulIntegrityRecord>>(store.execute(StatefulIntegrityRecord.change(subject.change(*changes.toTypedArray()))).statuses.single())
                val expected = image.copy(allocated = image.allocated.orEmpty() + (expectedNext to StateDetail("after backup")))
                assertEquals(expected.values(), store.execute(StatefulIntegrityRecord.get(subject, filterSoftDeleted = false)).values.single().values, "post-restore allocation, seed=$seed")
            }
        } finally {
            source.close()
            target.close()
        }
    }
}

private data class StateDetail(val note: String? = null, val numbers: List<Int>? = null, val attributes: Map<String, String>? = null) {
    fun values(): Values<StatefulIntegrityDetails> {
        val image = this
        return StatefulIntegrityDetails.create(setDefaults = false) {
            image.note?.let { note with it }
            image.numbers?.let { numbers with it.toList() }
            image.attributes?.let { attributes with it.toMap() }
        }
    }
}

private data class StateTyped(val kind: Int, val text: String?, val members: List<String>) {
    fun value(): TypedValue<MarykTypeEnum<out Any>, Any> = when (kind) {
        0 -> MarykTypeEnum.T1(text.orEmpty())
        1 -> MarykTypeEnum.T3(EmbeddedMarykModel.create(setDefaults = false) { text?.let { value with it } })
        else -> MarykTypeEnum.T4(members.toList())
    }
}

private data class StateImage(
    val label: String, val token: String,
    val details: StateDetail? = null, val items: List<Int>? = null, val boxes: List<StateDetail>? = null,
    val entries: Map<String, StateDetail>? = null, val tags: Set<String>? = null,
    val allocated: Map<UInt, StateDetail>? = null, val typed: StateTyped? = null,
    val typedBoxes: List<StateTyped>? = null,
) {
    fun values(): Values<StatefulIntegrityRecord> {
        val image = this
        return StatefulIntegrityRecord.create(setDefaults = false) {
            label with image.label
            token with image.token
            image.details?.let { details with it.values() }
            image.items?.let { items with it.toList() }
            image.boxes?.let { boxes with it.map(StateDetail::values) }
            image.entries?.let { entries with it.mapValues { (_, detail) -> detail.values() } }
            image.tags?.let { tags with it.toSet() }
            image.allocated?.let { allocated with it.mapValues { (_, detail) -> detail.values() } }
            image.typed?.let { typed with it.value() }
            image.typedBoxes?.let { typedBoxes with it.map(StateTyped::value) }
        }
    }
}

private data class StateSnapshot(val version: ULong, val image: StateImage, val deleted: Boolean)
