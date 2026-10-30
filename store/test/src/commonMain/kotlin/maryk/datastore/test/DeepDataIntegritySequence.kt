package maryk.datastore.test

import maryk.core.models.DataModel
import maryk.core.models.RootDataModel
import maryk.core.models.key
import maryk.core.properties.definitions.EmbeddedValuesDefinition
import maryk.core.properties.definitions.MapDefinition
import maryk.core.properties.definitions.NumberDefinition
import maryk.core.properties.definitions.StringDefinition
import maryk.core.properties.definitions.embed
import maryk.core.properties.definitions.incrementingMap
import maryk.core.properties.definitions.list
import maryk.core.properties.definitions.map
import maryk.core.properties.definitions.set
import maryk.core.properties.definitions.string
import maryk.core.properties.types.Key
import maryk.core.properties.types.numeric.SInt32
import maryk.core.properties.types.numeric.UInt32
import maryk.core.query.changes.Change
import maryk.core.query.changes.IncMapChange
import maryk.core.query.changes.IsChange
import maryk.core.query.changes.ObjectSoftDeleteChange
import maryk.core.query.changes.change
import maryk.core.query.filters.Equals
import maryk.core.query.pairs.with
import maryk.core.query.requests.ScanCursor
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
import kotlin.test.assertEquals
import kotlin.test.assertIs

object DeepIntegrityDetails : DataModel<DeepIntegrityDetails>() {
    val note by string(1u, required = false)
    val child by embed(2u, dataModel = { DeepIntegrityDetails }, required = false)
}

object DeepIntegrityRecord : RootDataModel<DeepIntegrityRecord>(indexes = {
    listOf(DeepIntegrityRecord.group.ref())
}) {
    val group by string(1u)
    val token by string(2u, unique = true, required = false)
    val details by embed(3u, dataModel = { DeepIntegrityDetails }, required = false)
    val items by list(4u, valueDefinition = NumberDefinition(type = SInt32), required = false)
    val entries by map(5u, keyDefinition = StringDefinition(),
        valueDefinition = EmbeddedValuesDefinition(dataModel = { DeepIntegrityDetails }), required = false)
    val labels by map(6u, keyDefinition = StringDefinition(), valueDefinition = StringDefinition(), required = false)
    val tags by set(7u, valueDefinition = StringDefinition(), required = false)
    val allocated by incrementingMap(8u, keyNumberDescriptor = UInt32,
        valueDefinition = MapDefinition(keyDefinition = StringDefinition(), valueDefinition = StringDefinition()),
        required = false)
}

/** One literal state machine, reused by each engine and a serialized backup target. */
class DeepDataIntegritySequence(private val source: IsDataStore, private val target: IsDataStore) {
    suspend fun preservesCurrentHistoryOwnershipAndPortableReplay() {
        fun key(id: Int) = DeepIntegrityRecord.key(ByteArray(DeepIntegrityRecord.Meta.keyByteSize).also {
            it[it.lastIndex] = id.toByte()
        })
        val first = key(1)
        val second = key(2)
        val transient = key(3)
        val fourth = key(4)
        var image = Image("alpha", Detail(), listOf(1, 2, 3), mapOf("four" to Detail()),
            linkedMapOf("" to "", "a.b" to "🚀"), linkedSetOf("", "a.b", "a\\b"))
        val history = mutableListOf<Snapshot>()

        suspend fun capture(version: ULong, deleted: Boolean) {
            val snapshot = Snapshot(version, image, deleted)
            history += snapshot
            assertImage(source, first, snapshot, historic = false)
        }
        suspend fun mutate(next: Image, deleted: Boolean = false, vararg changes: IsChange) {
            val status = assertIs<ChangeSuccess<DeepIntegrityRecord>>(source.execute(
                DeepIntegrityRecord.change(first.change(*changes)),
            ).statuses.single())
            image = next
            capture(status.version, deleted)
        }
        val added = assertIs<AddSuccess<DeepIntegrityRecord>>(source.execute(
            DeepIntegrityRecord.add(first to image.values()),
        ).statuses.single())
        capture(added.version, false)
        var secondImage = Image("beta")
        assertIs<AddSuccess<DeepIntegrityRecord>>(source.execute(DeepIntegrityRecord.add(second to secondImage.values())).statuses.single())

        mutate(image.copy(details = Detail("kept", Detail()), items = listOf(1, 22, 3)), changes = arrayOf(
            Change(DeepIntegrityRecord.ref { details { note } } with "kept"),
            Change(DeepIntegrityRecord.ref { details { child } } with Detail().values()),
            Change(DeepIntegrityRecord.ref { items at 1u } with 22),
        ))
        mutate(image.copy(items = listOf(22, 3)), changes = arrayOf(Change(DeepIntegrityRecord.ref { items at 0u } with null)))
        mutate(image.copy(items = listOf(9)), changes = arrayOf(Change(DeepIntegrityRecord.ref { items } with listOf(9))))
        mutate(image.copy(items = listOf(9, 10, 11)), changes = arrayOf(Change(DeepIntegrityRecord.ref { items } with listOf(9, 10, 11))))
        mutate(image.copy(items = listOf(9, 10, 12)), changes = arrayOf(Change(DeepIntegrityRecord.ref { items at 2u } with 12)))
        mutate(image.copy(items = listOf(10, 12)), changes = arrayOf(Change(DeepIntegrityRecord.ref { items at 0u } with null)))
        mutate(image.copy(details = null, labels = emptyMap(), tags = emptySet()), changes = arrayOf(Change(
            DeepIntegrityRecord.ref { details } with null,
            DeepIntegrityRecord.ref { labels } with emptyMap<String, String>(),
            DeepIntegrityRecord.ref { tags } with emptySet<String>(),
        )))
        mutate(image.copy(allocated = mapOf(1u to emptyMap(), 50u to mapOf("old" to "maximum"))), changes = arrayOf(Change(
            DeepIntegrityRecord.ref { allocated } with mapOf(1u to emptyMap(), 50u to mapOf("old" to "maximum")),
        )))
        mutate(image.copy(allocated = mapOf(1u to emptyMap())), changes = arrayOf(Change(
            DeepIntegrityRecord.ref { allocated at 50u } with null,
        )))

        val deleted = assertIs<DeleteSuccess<DeepIntegrityRecord>>(source.execute(DeepIntegrityRecord.delete(first)).statuses.single())
        capture(deleted.version, true)
        assertIs<ChangeSuccess<DeepIntegrityRecord>>(source.execute(DeepIntegrityRecord.change(second.change(
            Change(DeepIntegrityRecord.ref { token } with "alpha"),
        ))).statuses.single())
        secondImage = secondImage.copy(token = "alpha")
        assertIs<ValidationFail<DeepIntegrityRecord>>(source.execute(DeepIntegrityRecord.change(first.change(ObjectSoftDeleteChange(false)))).statuses.single())
        assertImage(source, first, history.last(), historic = false)
        mutate(image.copy(token = "gamma", details = Detail()), deleted = true, changes = arrayOf(Change(
            DeepIntegrityRecord.ref { token } with "gamma", DeepIntegrityRecord.ref { details } with Detail().values(),
        )))
        assertIs<AddSuccess<DeepIntegrityRecord>>(source.execute(DeepIntegrityRecord.add(transient to Image("gamma").values())).statuses.single())
        assertIs<ValidationFail<DeepIntegrityRecord>>(source.execute(DeepIntegrityRecord.change(first.change(ObjectSoftDeleteChange(false)))).statuses.single())
        assertIs<DeleteSuccess<DeepIntegrityRecord>>(source.execute(DeepIntegrityRecord.delete(transient, hardDelete = true)).statuses.single())
        mutate(image, changes = arrayOf(ObjectSoftDeleteChange(false)))
        capture(assertIs<DeleteSuccess<DeepIntegrityRecord>>(source.execute(DeepIntegrityRecord.delete(first)).statuses.single()).version, true)
        mutate(image, changes = arrayOf(ObjectSoftDeleteChange(false)))
        val secondDelete = assertIs<DeleteSuccess<DeepIntegrityRecord>>(source.execute(DeepIntegrityRecord.delete(second)).statuses.single())
        val fourthImage = Image("alpha")
        val fourthAdd = assertIs<AddSuccess<DeepIntegrityRecord>>(source.execute(DeepIntegrityRecord.add(fourth to fourthImage.values())).statuses.single())

        for (snapshot in history) assertImage(source, first, snapshot, historic = true)
        val backup = SerializedBackup(source)
        // This isolated sequence owns every source write, including this boundary.
        source.backup(backup, snapshotVersion = fourthAdd.version + 1uL, batchSize = 1u)
        assertEquals(3uL, target.restore(backup).records)
        for (snapshot in history) assertImage(target, first, snapshot, historic = true)
        assertImage(target, first, history.last(), historic = false)
        assertImage(target, second, Snapshot(secondDelete.version, secondImage, true), historic = false)
        assertEquals(fourthImage.values(), target.execute(DeepIntegrityRecord.get(fourth)).values.single().values)

        for (store in listOf(source, target)) {
            assertIs<ChangeSuccess<DeepIntegrityRecord>>(store.execute(DeepIntegrityRecord.change(first.change(
                IncMapChange(DeepIntegrityRecord.ref { allocated }.change(addValues = listOf(emptyMap<String, String>()))),
            ))).statuses.single())
            assertEquals(mapOf(1u to emptyMap(), 51u to emptyMap()),
                store.execute(DeepIntegrityRecord.get(first)).values.single().values { allocated },
                "Allocation maximum must survive deletion and serialized history restore")
            assertEquals(setOf(first, fourth), store.execute(DeepIntegrityRecord.scan(
                where = Equals(DeepIntegrityRecord.ref { group } with "same"),
            )).values.map { it.key }.toSet())
            assertEquals(listOf(fourth), store.execute(DeepIntegrityRecord.scan(
                where = Equals(DeepIntegrityRecord.ref { token } with "alpha"),
            )).values.map { it.key })
            val keys = mutableListOf<Key<DeepIntegrityRecord>>()
            var cursor: ScanCursor? = null
            do {
                val page = store.execute(DeepIntegrityRecord.scan(cursor = cursor, limit = 1u,
                    filterSoftDeleted = false, allowTableScan = true))
                keys += page.values.map { it.key }
                cursor = page.nextCursor
            } while (cursor != null)
            assertEquals(listOf(first, second, fourth), keys)
        }
    }

    private suspend fun assertImage(store: IsDataStore, key: Key<DeepIntegrityRecord>, expected: Snapshot, historic: Boolean) {
        val actual = store.execute(DeepIntegrityRecord.get(key, filterSoftDeleted = false,
            toVersion = expected.version.takeIf { historic })).values.single()
        assertEquals(expected.image.values(), actual.values, "values at ${expected.version}, historic=$historic")
        assertEquals(expected.deleted, actual.isDeleted, "deletion at ${expected.version}, historic=$historic")
    }

    private data class Snapshot(val version: ULong, val image: Image, val deleted: Boolean)
    private data class Detail(val note: String? = null, val child: Detail? = null) {
        fun values(): Values<DeepIntegrityDetails> {
            val detail = this
            return DeepIntegrityDetails.create(setDefaults = false) {
                detail.note?.let { note with it }
                detail.child?.let { child with it.values() }
            }
        }
    }
    private data class Image(val token: String?, val details: Detail? = null, val items: List<Int>? = null,
        val entries: Map<String, Detail>? = null, val labels: Map<String, String>? = null, val tags: Set<String>? = null,
        val allocated: Map<UInt, Map<String, String>>? = null) {
        fun values(): Values<DeepIntegrityRecord> {
            val image = this
            return DeepIntegrityRecord.create(setDefaults = false) {
                group with "same"
                image.token?.let { token with it }
                image.details?.let { details with it.values() }
                image.items?.let { items with it }
                image.allocated?.let { allocated with it }
                image.entries?.let { entries with it.mapValues { (_, value) -> value.values() } }
                image.labels?.let { labels with it }
                image.tags?.let { tags with it }
            }
        }
    }
}
