package maryk.datastore.test

import maryk.core.properties.IsPropertyContext
import maryk.core.properties.definitions.IsListDefinition
import maryk.core.properties.definitions.IsSetDefinition
import maryk.core.properties.definitions.IsMapDefinition
import maryk.core.properties.definitions.IsEmbeddedValuesDefinition
import maryk.core.values.Values
import maryk.core.models.graph
import maryk.core.models.DataModel
import maryk.core.models.RootDataModel
import maryk.core.properties.definitions.EmbeddedValuesDefinition
import maryk.core.properties.definitions.ListDefinition
import maryk.core.properties.definitions.MapDefinition
import maryk.core.properties.definitions.SetDefinition
import maryk.core.properties.definitions.StringDefinition
import maryk.core.properties.definitions.IsSerializablePropertyDefinition
import maryk.core.properties.definitions.embed
import maryk.core.properties.definitions.list
import maryk.core.properties.definitions.map
import maryk.core.properties.definitions.set
import maryk.core.properties.definitions.string
import maryk.core.properties.graph.graph
import maryk.core.properties.references.AnyPropertyReference
import maryk.core.properties.references.IsPropertyReference
import maryk.core.query.changes.Change
import maryk.core.query.changes.change
import maryk.core.query.filters.And
import maryk.core.query.filters.Equals
import maryk.core.query.filters.Exists
import maryk.core.query.filters.Not
import maryk.core.query.filters.Or
import maryk.core.query.pairs.with
import maryk.core.query.requests.add
import maryk.core.query.requests.change
import maryk.core.query.requests.scan
import maryk.core.query.responses.statuses.AddSuccess
import maryk.core.query.responses.statuses.ChangeSuccess
import maryk.datastore.shared.IsDataStore
import maryk.datastore.shared.encryption.AesGcmHmacSha256EncryptionProvider
import maryk.datastore.shared.encryption.ContextualFieldEncryptionProvider
import maryk.datastore.shared.encryption.FieldEncryptionContext
import kotlin.test.assertEquals
import kotlin.test.assertIs

object CollectionPresenceDetails : DataModel<CollectionPresenceDetails>() {
    val items by list(1u, required = false, valueDefinition = StringDefinition())
    val members by set(2u, required = false, valueDefinition = StringDefinition())
    val mapping by map(3u, required = false, keyDefinition = StringDefinition(), valueDefinition = StringDefinition())
}

object CollectionPresenceRecord : RootDataModel<CollectionPresenceRecord>() {
    val items by list(1u, required = false, valueDefinition = StringDefinition())
    val members by set(2u, required = false, valueDefinition = StringDefinition())
    val mapping by map(3u, required = false, keyDefinition = StringDefinition(), valueDefinition = StringDefinition())
    val details by embed(4u, required = false, dataModel = { CollectionPresenceDetails })
    val nestedList by map(5u, required = false, keyDefinition = StringDefinition(), valueDefinition = ListDefinition(valueDefinition = StringDefinition()))
    val nestedSet by map(6u, required = false, keyDefinition = StringDefinition(), valueDefinition = SetDefinition(valueDefinition = StringDefinition()))
    val nestedMap by map(7u, required = false, keyDefinition = StringDefinition(), valueDefinition = MapDefinition(keyDefinition = StringDefinition(), valueDefinition = StringDefinition()))
    val label by string(8u)
    val secret by string(9u, sensitive = true, required = false)
    val detailItems by list(10u, required = false, valueDefinition = EmbeddedValuesDefinition(dataModel = { CollectionPresenceDetails }))
}

suspend fun assertEmptyCollectionPresence(store: IsDataStore) = assertCollectionFilters(store, false)

suspend fun assertComplexCollectionComparisons(store: IsDataStore) = assertCollectionFilters(store, true)

private suspend fun assertCollectionFilters(store: IsDataStore, comparisonsOnly: Boolean) {
    fun values(labelValue: String, empty: Boolean) = CollectionPresenceRecord.create {
        label with labelValue
        items with if (empty) emptyList() else listOf("value")
        members with if (empty) emptySet() else setOf("value")
        mapping with if (empty) emptyMap() else mapOf("key" to "value")
        details with CollectionPresenceDetails.create {
            items with if (empty) emptyList() else listOf("value")
            members with if (empty) emptySet() else setOf("value")
            mapping with if (empty) emptyMap() else mapOf("key" to "value")
        }
        nestedList with mapOf("slot" to if (empty) emptyList() else listOf("value"))
        nestedSet with mapOf("slot" to if (empty) emptySet() else setOf("value"))
        nestedMap with mapOf("slot" to if (empty) emptyMap() else mapOf("key" to "value"))
        detailItems with listOf(
            CollectionPresenceDetails.create { items with listOf("unselected"); mapping with mapOf("unselected" to "first") },
            CollectionPresenceDetails.create {
                items with if (empty) emptyList() else listOf("value")
                members with if (empty) emptySet() else setOf("value")
                mapping with if (empty) emptyMap() else mapOf("key" to "value")
            },
        )
    }
    val absent = assertIs<AddSuccess<CollectionPresenceRecord>>(store.execute(CollectionPresenceRecord.add(
        CollectionPresenceRecord.create { label with "absent" },
    )).statuses.single())
    val empty = assertIs<AddSuccess<CollectionPresenceRecord>>(store.execute(CollectionPresenceRecord.add(values("empty", true))).statuses.single())
    val full = assertIs<AddSuccess<CollectionPresenceRecord>>(store.execute(CollectionPresenceRecord.add(values("full", false))).statuses.single())
    val removed = assertIs<AddSuccess<CollectionPresenceRecord>>(store.execute(CollectionPresenceRecord.add(values("removed", false))).statuses.single())
    val deletion = assertIs<ChangeSuccess<CollectionPresenceRecord>>(store.execute(CollectionPresenceRecord.change(removed.key.change(Change(
        CollectionPresenceRecord.ref { items } with null,
        CollectionPresenceRecord.ref { members } with null,
        CollectionPresenceRecord.ref { mapping } with null,
        CollectionPresenceRecord.ref { details } with null,
        CollectionPresenceRecord.ref { nestedList } with null,
        CollectionPresenceRecord.ref { nestedSet } with null,
        CollectionPresenceRecord.ref { nestedMap } with null,
        CollectionPresenceRecord.ref { detailItems } with null,
    )))).statuses.single())
    val references: List<AnyPropertyReference> = listOf(
        CollectionPresenceRecord.ref { items }, CollectionPresenceRecord.ref { members }, CollectionPresenceRecord.ref { mapping },
        CollectionPresenceRecord.ref { details }, CollectionPresenceRecord.ref { details { items } },
        CollectionPresenceRecord.ref { details { members } }, CollectionPresenceRecord.ref { details { mapping } },
        CollectionPresenceRecord.ref { nestedList at "slot" }, CollectionPresenceRecord.ref { nestedSet at "slot" },
        CollectionPresenceRecord.ref { nestedMap at "slot" },
        CollectionPresenceRecord.ref { detailItems at 1u },
        CollectionPresenceDetails.ref(CollectionPresenceRecord.ref { detailItems at 1u }) { items },
        CollectionPresenceDetails.ref(CollectionPresenceRecord.ref { detailItems at 1u }) { members },
        CollectionPresenceDetails.ref(CollectionPresenceRecord.ref { detailItems at 1u }) { mapping },
    )
    for (reference in references) {
        @Suppress("UNCHECKED_CAST")
        val exists = Exists(reference as IsPropertyReference<*, IsSerializablePropertyDefinition<*, *>, *>)
        for (version in listOf(null, removed.version, deletion.version)) {
            val expected = if (version == removed.version) setOf(empty.key, full.key, removed.key) else setOf(empty.key, full.key)
            if (comparisonsOnly) {
                for (emptyExpected in listOf(true, false)) {
                    @Suppress("UNCHECKED_CAST")
                    val expectedValue = requireNotNull(values("ignored", emptyExpected)[reference as IsPropertyReference<Any, *, *>])
                    val expectedKeys = when {
                        emptyExpected && expectedValue is Collection<*> -> expected
                        emptyExpected -> setOf(empty.key)
                        version == removed.version -> setOf(full.key, removed.key)
                        else -> setOf(full.key)
                    }
                    @Suppress("UNCHECKED_CAST")
                    val pair = when (expectedValue) {
                        is List<*> -> (reference as IsPropertyReference<List<Any>, IsListDefinition<Any, IsPropertyContext>, *>) with (expectedValue as List<Any>)
                        is Set<*> -> (reference as IsPropertyReference<Set<Any>, IsSetDefinition<Any, IsPropertyContext>, *>) with (expectedValue as Set<Any>)
                        is Map<*, *> -> (reference as IsPropertyReference<Map<Any, Any>, IsMapDefinition<Any, Any, IsPropertyContext>, *>) with (expectedValue as Map<Any, Any>)
                        is Values<*> -> (reference as IsPropertyReference<Values<CollectionPresenceDetails>, IsEmbeddedValuesDefinition<CollectionPresenceDetails, IsPropertyContext>, *>) with (expectedValue as Values<CollectionPresenceDetails>)
                        else -> error("Unexpected complex value")
                    }
                    val response = store.execute(CollectionPresenceRecord.scan(
                        where = Equals(pair), toVersion = version, allowTableScan = true,
                    ))
                    assertEquals(expectedKeys, response.values.map { it.key }.toSet(), "Equals(${reference.completeName}, $expectedValue), version=$version")
                }
                continue
            }
            val present = store.execute(CollectionPresenceRecord.scan(where = exists, toVersion = version, allowTableScan = true))
            assertEquals(expected, present.values.map { it.key }.toSet(), "Exists(${reference.completeName}), version=$version")
            val missing = store.execute(CollectionPresenceRecord.scan(where = Not(exists), toVersion = version, allowTableScan = true))
            assertEquals(setOf(absent.key, empty.key, full.key, removed.key) - expected, missing.values.map { it.key }.toSet(), "Not Exists(${reference.completeName}), version=$version")
            val mixed = store.execute(CollectionPresenceRecord.scan(where = And(exists, Equals(CollectionPresenceRecord.ref { label } with "empty")), toVersion = version, allowTableScan = true))
            assertEquals(setOf(empty.key), mixed.values.map { it.key }.toSet(), "mixed comparison, ${reference.completeName}")
            val either = store.execute(CollectionPresenceRecord.scan(where = Or(exists, Equals(CollectionPresenceRecord.ref { label } with "absent")), toVersion = version, allowTableScan = true))
            assertEquals(expected + absent.key, either.values.map { it.key }.toSet(), "Or Exists(${reference.completeName}), version=$version")
        }
    }
}

class CollectionFilterEncryptionProvider(
    private val delegate: AesGcmHmacSha256EncryptionProvider = AesGcmHmacSha256EncryptionProvider(
        ByteArray(32) { it.toByte() }, ByteArray(32) { (it + 32).toByte() },
    ),
) : ContextualFieldEncryptionProvider by delegate {
    var rejectDecrypt = false
    override suspend fun decrypt(value: ByteArray, offset: Int, length: Int): ByteArray {
        check(!rejectDecrypt) { "Filter must not decrypt an unselected secret" }
        return delegate.decrypt(value, offset, length)
    }
    override suspend fun decrypt(context: FieldEncryptionContext, value: ByteArray, offset: Int, length: Int): ByteArray {
        check(!rejectDecrypt) { "Filter must not decrypt an unselected secret" }
        return delegate.decrypt(context, value, offset, length)
    }
}

suspend fun assertCollectionFilterEncryptionIsolation(store: IsDataStore, provider: CollectionFilterEncryptionProvider) {
    fun values(labelValue: String, empty: Boolean) = CollectionPresenceRecord.create {
        label with labelValue
        secret with "secret"
        items with if (empty) emptyList() else listOf("value")
        mapping with if (empty) emptyMap() else mapOf("key" to "value")
        nestedList with mapOf("slot" to if (empty) emptyList() else listOf("value"))
    }
    val absent = assertIs<AddSuccess<CollectionPresenceRecord>>(store.execute(CollectionPresenceRecord.add(
        CollectionPresenceRecord.create { label with "absent" },
    )).statuses.single())
    val empty = assertIs<AddSuccess<CollectionPresenceRecord>>(store.execute(CollectionPresenceRecord.add(values("empty", true))).statuses.single())
    val full = assertIs<AddSuccess<CollectionPresenceRecord>>(store.execute(CollectionPresenceRecord.add(values("full", false))).statuses.single())
    val removed = assertIs<AddSuccess<CollectionPresenceRecord>>(store.execute(CollectionPresenceRecord.add(values("removed", false))).statuses.single())
    val deletion = assertIs<ChangeSuccess<CollectionPresenceRecord>>(store.execute(CollectionPresenceRecord.change(removed.key.change(
        Change(CollectionPresenceRecord.ref { secret } with null),
    ))).statuses.single())
    provider.rejectDecrypt = true
    val select = CollectionPresenceRecord.graph { listOf(label) }
    val exists = Exists(CollectionPresenceRecord.ref { secret })
    for (version in listOf(null, removed.version, deletion.version)) {
        val expected = if (version == removed.version) setOf(empty.key, full.key, removed.key) else setOf(empty.key, full.key)
        val present = store.execute(CollectionPresenceRecord.scan(where = exists, select = select, toVersion = version, allowTableScan = true))
        assertEquals(expected, present.values.map { it.key }.toSet(), "encrypted Exists, version=$version")
        val missing = store.execute(CollectionPresenceRecord.scan(where = Not(exists), select = select, toVersion = version, allowTableScan = true))
        assertEquals(setOf(absent.key, empty.key, full.key, removed.key) - expected, missing.values.map { it.key }.toSet())
        for (filter in listOf(
            Equals(CollectionPresenceRecord.ref { items } with emptyList<String>()),
            Equals(CollectionPresenceRecord.ref { nestedList at "slot" } with emptyList<String>()),
        )) {
            val compared = store.execute(CollectionPresenceRecord.scan(where = filter, select = select, toVersion = version, allowTableScan = true))
            assertEquals(setOf(empty.key, full.key, removed.key), compared.values.map { it.key }.toSet(), "unrelated encrypted sibling, version=$version")
        }
        val mixed = store.execute(CollectionPresenceRecord.scan(
            where = And(exists, Equals(CollectionPresenceRecord.ref { mapping } with emptyMap<String, String>())),
            select = select, toVersion = version, allowTableScan = true,
        ))
        assertEquals(setOf(empty.key), mixed.values.map { it.key }.toSet())
    }
}
