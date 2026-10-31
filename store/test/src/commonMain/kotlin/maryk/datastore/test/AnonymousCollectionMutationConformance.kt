package maryk.datastore.test

import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue
import maryk.core.models.DataModel
import maryk.core.models.RootDataModel
import maryk.core.properties.IsPropertyContext
import maryk.core.properties.definitions.EmbeddedValuesDefinition
import maryk.core.properties.definitions.IsListDefinition
import maryk.core.properties.definitions.IsSetDefinition
import maryk.core.properties.definitions.ListDefinition
import maryk.core.properties.definitions.MapDefinition
import maryk.core.properties.definitions.MultiTypeDefinition
import maryk.core.properties.definitions.SetDefinition
import maryk.core.properties.definitions.StringDefinition
import maryk.core.properties.definitions.embed
import maryk.core.properties.definitions.incrementingMap
import maryk.core.properties.definitions.list
import maryk.core.properties.definitions.map
import maryk.core.properties.definitions.multiType
import maryk.core.properties.definitions.set
import maryk.core.properties.definitions.string
import maryk.core.properties.exceptions.TooManyItemsException
import maryk.core.properties.references.CanContainListItemReference
import maryk.core.properties.references.CanContainSetItemReference
import maryk.core.properties.references.IsPropertyReference
import maryk.core.properties.references.MapValueReference
import maryk.core.properties.types.TypedValue
import maryk.core.properties.types.invoke
import maryk.core.properties.types.numeric.UInt32
import maryk.core.query.changes.Change
import maryk.core.query.changes.IncMapChange
import maryk.core.query.changes.IsChange
import maryk.core.query.changes.ListChange
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
import maryk.core.query.responses.statuses.ServerFail
import maryk.core.query.responses.statuses.ValidationFail
import maryk.core.values.Values
import maryk.datastore.shared.IsDataStore
import maryk.test.models.MarykTypeEnum
import maryk.test.models.MarykTypeEnum.T4
import maryk.test.models.MarykTypeEnum.T5

object AnonymousCollectionRecord : RootDataModel<AnonymousCollectionRecord>(indexes = { listOf(AnonymousCollectionRecord.label.ref()) }) {
    val lists by map(1u, maxSize = 2u, keyDefinition = StringDefinition(final = true), valueDefinition = ListDefinition(valueDefinition = StringDefinition(minSize = 3u)))
    val sets by map(2u, maxSize = 2u, keyDefinition = StringDefinition(final = true), valueDefinition = SetDefinition(valueDefinition = StringDefinition(minSize = 3u)))
    val nested by map(3u, keyDefinition = StringDefinition(), valueDefinition = MapDefinition(
        keyDefinition = StringDefinition(final = true), valueDefinition = ListDefinition(valueDefinition = StringDefinition(minSize = 3u))))
    val typed by multiType(4u, typeEnum = MarykTypeEnum, typeIsFinal = false)
    val optional by map(5u, keyDefinition = StringDefinition(), valueDefinition = ListDefinition(valueDefinition = StringDefinition()), required = false)
    val label by string(6u)
    val optionalChild by embed(9u, dataModel = { AnonymousCollectionChild }, required = false)
    val embeddedEntries by map(10u, keyDefinition = StringDefinition(minSize = 3u, final = true), valueDefinition = EmbeddedValuesDefinition(dataModel = { AnonymousCollectionChild }), required = false)
    val children by list(8u, minSize = 1u, valueDefinition = EmbeddedValuesDefinition(dataModel = { AnonymousCollectionChild }), required = false)
    val typedEntries by map(7u, keyDefinition = StringDefinition(), valueDefinition = MultiTypeDefinition(typeEnum = MarykTypeEnum), required = false)
}

object AnonymousCollectionChild : DataModel<AnonymousCollectionChild>() {
    val items by list(1u, valueDefinition = StringDefinition())
    val members by set(2u, valueDefinition = StringDefinition())
    val allocated by incrementingMap(3u, maxSize = 1u, keyNumberDescriptor = UInt32, valueDefinition = StringDefinition(), required = false)
}

/** Literal collection images exercise the same references through mutation, history and rejection. */
@Suppress("UNCHECKED_CAST")
suspend fun assertAnonymousCollectionMutations(store: IsDataStore) {
    val model = AnonymousCollectionRecord
    var lists = mapOf("a" to listOf("one", "two", "one"))
    var sets = mapOf("a" to setOf("one"))
    var nested = mapOf("outer" to mapOf("inner" to listOf("nest")))
    var typed: TypedValue<MarykTypeEnum<out Any>, Any> = T4(listOf("typed"))
    var optional: Map<String, List<String>>? = null
    var label = "original"
    var typedEntries = mapOf<String, TypedValue<MarykTypeEnum<out Any>, Any>>("locked" to T4(listOf("initial")))
    fun image() = model.create {
        this.lists with lists
        this.sets with sets
        this.nested with nested
        this.typed with typed
        this.optional with optional
        this.label with label
        this.typedEntries with typedEntries
    }
    fun listRef(name: String) = model.getPropertyReferenceByName(name) as IsPropertyReference<List<String>, IsListDefinition<String, IsPropertyContext>, *>
    fun setRef(name: String) = model.getPropertyReferenceByName(name) as IsPropertyReference<Set<String>, IsSetDefinition<String, IsPropertyContext>, *>
    val added = assertIs<AddSuccess<AnonymousCollectionRecord>>(store.execute(model.add(image())).statuses.single())
    var version = added.version
    val history = mutableListOf<Pair<ULong, Values<AnonymousCollectionRecord>>>(version to image())
    suspend fun apply(context: String, vararg changes: IsChange) {
        val response = store.execute(model.change(added.key.change(*changes)))
        val status = assertIs<ChangeSuccess<AnonymousCollectionRecord>>(response.statuses.single(), "$context: $response")
        version = status.version
        assertEquals(image(), store.execute(model.get(added.key)).values.single().values, context)
        if (history.last().first != version) history += version to image()
    }
    suspend fun reject(context: String, change: IsChange, validationOnly: Boolean = false) {
        val response = store.execute(model.change(added.key.change(Change(model.ref { this.label } with "must roll back"), change)))
        if (validationOnly) assertIs<ValidationFail<AnonymousCollectionRecord>>(response.statuses.single(), "$context: $response")
        else assertTrue(response.statuses.single() is ValidationFail<*> || response.statuses.single() is ServerFail<*>, "$context: $response")
        val current = store.execute(model.get(added.key)).values.single()
        assertEquals(image(), current.values, context)
        assertEquals(version, current.lastVersion, context)
        assertEquals(emptyList(), store.execute(model.scan(where = Equals(model.ref { this.label } with "must roll back"))).values)
    }
    val a = listRef("lists.@a")
    lists = lists + ("a" to listOf("two", "insert", "one", "tail"))
    apply("existing map list", ListChange(a.change(deleteValues = listOf("one"), addValuesAtIndex = mapOf(1u to "insert"), addValuesToEnd = listOf("tail"))))
    sets = sets + ("a" to setOf("one", "two"))
    apply("existing map set", SetChange(setRef("sets.@a").change(addValues = setOf("two"))))
    lists = lists + ("b" to listOf("new"))
    sets = sets + ("b" to setOf("new"))
    apply("new map entries with final key definitions", ListChange(listRef("lists.@b").change(addValuesToEnd = listOf("new"))),
        SetChange(setRef("sets.@b").change(addValues = setOf("new"))))
    lists = lists + ("b" to listOf("replacement"))
    sets = sets + ("b" to setOf("replacement"))
    apply("replace values in full maps", Change(listRef("lists.@b") with listOf("replacement"), setRef("sets.@b") with setOf("replacement")))
    val typedEntry = model.getPropertyReferenceByName("typedEntries.@locked") as MapValueReference<String, TypedValue<MarykTypeEnum<out Any>, Any>, IsPropertyContext>
    reject("map value type is final", Change(typedEntry with T5(setOf("changed"))), validationOnly = true)
    typedEntries = mapOf("locked" to T4(listOf("changed")))
    apply("same type replacement preserves map cardinality", Change(typedEntry with T4(listOf("changed"))))
    typedEntries = emptyMap()
    apply("delete typed map value", Change(typedEntry with null))
    val overflow = store.execute(model.change(added.key.change(
        ListChange(listRef("lists.@third").change(addValuesToEnd = listOf("valid"))),
    )))
    val rejected = assertIs<ValidationFail<AnonymousCollectionRecord>>(overflow.statuses.single())
    assertEquals("lists", assertIs<TooManyItemsException>(rejected.exceptions.single()).reference?.completeName)
    assertEquals(image(), store.execute(model.get(added.key)).values.single().values)
    nested = nested + ("outer" to (nested.getValue("outer") + ("later" to listOf("new"))))
    apply("nested map entry", ListChange(listRef("nested.@outer.@later").change(addValuesToEnd = listOf("new"))))
    typed = T4(listOf("typed", "more"))
    apply("active typed list", ListChange(listRef("typed.*T4").change(addValuesToEnd = listOf("more"))))
    typed = T4(listOf("replacement"))
    apply("replace active typed list", Change(listRef("typed.*T4") with listOf("replacement")))
    typed = T5(setOf("typed"))
    apply("switch to typed set", Change(model.ref { this.typed } with typed))
    typed = T5(setOf("typed", "more"))
    apply("active typed set", SetChange(setRef("typed.*T5").change(addValues = setOf("more"))))
    typed = T5(setOf("replacement"))
    apply("replace active typed set", Change(setRef("typed.*T5") with setOf("replacement")))
    reject("invalid list member", ListChange(a.change(addValuesToEnd = listOf("x"))), validationOnly = true)
    reject("invalid set member", SetChange(setRef("sets.@a").change(addValues = setOf("x"))), validationOnly = true)
    reject("replacement under missing nested map", Change(listRef("nested.@absent.@child") with listOf("new")))
    reject("missing ancestor", ListChange(listRef("optional.@missing").change(addValuesToEnd = listOf("new"))))
    optional = mapOf("first" to listOf("new"))
    apply("replacement initializes immediate map property", Change(listRef("optional.@first") with listOf("new")))
    optional = null
    apply("remove optional map", Change(model.ref { this.optional } with null))
    optional = mapOf("missing" to listOf("new"))
    apply("staged parent and child in one change", Change(model.ref { this.optional } with emptyMap(), listRef("optional.@missing") with listOf("new")))
    label = "after no-op"
    apply("empty deltas preserve absent entries", Change(model.ref { this.label } with label),
        ListChange(listRef("lists.@absent").change(addValuesToEnd = emptyList())), SetChange(setRef("sets.@absent").change(addValues = emptySet())))
    val listItem = a.propertyDefinition.itemRef(1u, a as CanContainListItemReference<*, *, *>)
    lists = lists + ("a" to listOf("two", "one", "tail"))
    apply("anonymous list item deletion", Change(listItem with null))
    val setReference = setRef("sets.@a")
    val setDefinition = setReference.comparablePropertyDefinition
    sets = sets + ("a" to setOf("two"))
    apply("anonymous set item deletion", Change(setDefinition.itemRef("one", setReference as CanContainSetItemReference<*, *, *>) with null))
    nested = nested + ("outer" to mapOf("inner" to listOf("nest")))
    apply("remove nested map entry", Change((model.getPropertyReferenceByName("nested.@outer.@later") as MapValueReference<String, List<String>, IsPropertyContext>) with null))
    if (store.keepAllVersions) for ((oldVersion, snapshot) in history) {
        assertEquals(snapshot, store.execute(model.get(added.key, toVersion = oldVersion)).values.single().values, "History $oldVersion")
    }
}

/** A delta can initialize its collection, but cannot append an enclosing list item implicitly. */
@Suppress("UNCHECKED_CAST")
suspend fun assertCollectionTargetsRespectListBounds(store: IsDataStore) {
    val model = AnonymousCollectionRecord
    val original = model.create {
        lists with emptyMap<String, List<String>>()
        sets with emptyMap<String, Set<String>>()
        nested with emptyMap<String, Map<String, List<String>>>()
        typed with T4(listOf("typed"))
        label with "original"
        children with listOf(AnonymousCollectionChild.create {
            items with listOf("first")
            members with setOf("first")
        })
    }
    val added = assertIs<AddSuccess<AnonymousCollectionRecord>>(store.execute(model.add(original)).statuses.single())
    fun listRef(index: UInt) = model.getPropertyReferenceByName("children.@$index.items") as IsPropertyReference<List<String>, IsListDefinition<String, IsPropertyContext>, *>
    fun setRef(index: UInt) = model.getPropertyReferenceByName("children.@$index.members") as IsPropertyReference<Set<String>, IsSetDefinition<String, IsPropertyContext>, *>
    for (change in listOf(
        ListChange(listRef(1u).change(addValuesToEnd = listOf("orphan"))),
        SetChange(setRef(1u).change(addValues = setOf("orphan"))),
        Change(model.ref { children at 1u } with AnonymousCollectionChild.create {
            items with listOf("orphan")
            members with setOf("orphan")
        }),
        Change(model.ref { optionalChild { items } } with listOf("orphan")),
        ListChange(model.ref { optionalChild { items } }.change(addValuesToEnd = listOf("orphan"))),
        SetChange(model.ref { optionalChild { members } }.change(addValues = setOf("orphan"))),
        IncMapChange(model.ref { optionalChild { allocated } }.change(addValues = listOf("orphan"))),
    )) {
        val response = store.execute(model.change(added.key.change(Change(model.ref { label } with "must roll back"), change)))
        assertTrue(response.statuses.single() is ServerFail<*> || response.statuses.single() is ValidationFail<*>, response.toString())
        val current = store.execute(model.get(added.key)).values.single()
        assertEquals(original, current.values)
        assertEquals(added.version, current.lastVersion)
    }
    val existingList = listRef(0u)
    val absentItem = existingList.propertyDefinition.itemRef(99u, existingList as CanContainListItemReference<*, *, *>)
    val absentDeletion = assertIs<ChangeSuccess<AnonymousCollectionRecord>>(store.execute(model.change(added.key.change(Change(
        model.ref { children at 1u } with null,
        absentItem with null,
    )))).statuses.single())
    assertEquals(added.version, absentDeletion.version)
    assertEquals(original, store.execute(model.get(added.key)).values.single().values)
    val invalidKey = store.execute(model.change(added.key.change(Change(
        model.ref { embeddedEntries at "x" } with AnonymousCollectionChild.create {
            items with listOf("valid")
            members with setOf("valid")
        },
    ))))
    assertIs<ValidationFail<AnonymousCollectionRecord>>(invalidKey.statuses.single())
    assertEquals(original, store.execute(model.get(added.key)).values.single().values)
    val valid = store.execute(model.change(added.key.change(
        ListChange(listRef(0u).change(addValuesToEnd = listOf("second"))),
        SetChange(setRef(0u).change(addValues = setOf("second"))),
        Change(
            model.ref { optionalChild } with AnonymousCollectionChild.create {
                items with listOf("initial")
                members with setOf("initial")
            },
            model.ref { embeddedEntries at "valid" } with AnonymousCollectionChild.create {
                items with listOf("valid")
                members with setOf("valid")
            },
        ),
        IncMapChange(model.ref { optionalChild { allocated } }.change(addValues = listOf("allocated"))),
    )))
    assertIs<ChangeSuccess<AnonymousCollectionRecord>>(valid.statuses.single())
    val current = store.execute(model.get(added.key)).values.single().values
    val initializedChild = (current { optionalChild })!!
    assertEquals(mapOf(1u to "allocated"), initializedChild { allocated })
    assertEquals(setOf("valid"), (current { embeddedEntries })!!.keys)
    val currentChild = (current { children })!!.single()
    assertEquals(listOf("first", "second"), currentChild { items })
    assertEquals(setOf("first", "second"), currentChild { members })
    assertIs<ChangeSuccess<AnonymousCollectionRecord>>(store.execute(model.change(added.key.change(
        Change(model.ref { children at 0u } with AnonymousCollectionChild.create {
            items with listOf("replacement")
            members with setOf("replacement")
        }),
    ))).statuses.single())
    val replaced = store.execute(model.get(added.key)).values.single().values
    val replacedChild = (replaced { children })!!.single()
    assertEquals(listOf("replacement"), replacedChild { items })
    assertEquals(setOf("replacement"), replacedChild { members })
    if (store.keepAllVersions) {
        assertEquals(original, store.execute(model.get(added.key, toVersion = added.version)).values.single().values)
        assertEquals(current, store.execute(model.get(added.key, toVersion = assertIs<ChangeSuccess<AnonymousCollectionRecord>>(valid.statuses.single()).version)).values.single().values)
    }
}

/** Deleted rows still reject malformed supplied keys and invalid collection cardinalities. */
@Suppress("UNCHECKED_CAST")
suspend fun assertDeletedCollectionConstraints(store: IsDataStore) {
    val model = AnonymousCollectionRecord
    val child = AnonymousCollectionChild.create {
        items with listOf("original")
        members with setOf("original")
        allocated with mapOf(1u to "original")
    }
    val original = model.create {
        lists with mapOf("one" to listOf("original"), "two" to listOf("original"))
        sets with mapOf("one" to setOf("original"), "two" to setOf("original"))
        nested with emptyMap<String, Map<String, List<String>>>()
        typed with T4(listOf("original"))
        label with "original"
        children with listOf(child)
        optionalChild with child
        embeddedEntries with emptyMap<String, Values<AnonymousCollectionChild>>()
    }
    val added = assertIs<AddSuccess<AnonymousCollectionRecord>>(store.execute(model.add(original)).statuses.single())
    val thirdList = model.getPropertyReferenceByName("lists.@third") as IsPropertyReference<List<String>, IsListDefinition<String, IsPropertyContext>, *>
    for (deleted in listOf(false, true)) {
        if (deleted) store.execute(model.delete(added.key))
        val version = store.execute(model.get(added.key, filterSoftDeleted = false)).values.single().lastVersion
        for (change in listOf(
            Change(model.ref { embeddedEntries at "x" } with child),
            ListChange(thirdList.change(addValuesToEnd = listOf("valid"))),
            SetChange(model.ref { sets at "third" }.change(addValues = setOf("valid"))),
            IncMapChange(model.ref { optionalChild { allocated } }.change(addValues = listOf("extra"))),
            Change(model.ref { children at 0u } with null),
        )) {
            val response = store.execute(model.change(added.key.change(Change(model.ref { label } with "must roll back"), change)))
            assertIs<ValidationFail<AnonymousCollectionRecord>>(response.statuses.single(), "$deleted: $response")
            val current = store.execute(model.get(added.key, filterSoftDeleted = false)).values.single()
            assertEquals(original, current.values)
            assertEquals(version, current.lastVersion)
            assertEquals(deleted, current.isDeleted)
        }
    }
    if (store.keepAllVersions) {
        assertEquals(original, store.execute(model.get(added.key, toVersion = added.version)).values.single().values)
    }
}
