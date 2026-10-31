package maryk.datastore.test

import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue
import maryk.core.models.RootDataModel
import maryk.core.models.key
import maryk.core.properties.IsPropertyContext
import maryk.core.properties.definitions.IsListDefinition
import maryk.core.properties.definitions.ListDefinition
import maryk.core.properties.definitions.SetDefinition
import maryk.core.properties.definitions.StringDefinition
import maryk.core.properties.definitions.contextual.DataModelReference
import maryk.core.properties.definitions.embed
import maryk.core.properties.definitions.incrementingMap
import maryk.core.properties.definitions.list
import maryk.core.properties.definitions.map
import maryk.core.properties.definitions.multiType
import maryk.core.properties.definitions.number
import maryk.core.properties.definitions.set
import maryk.core.properties.definitions.string
import maryk.core.properties.references.IsPropertyReference
import maryk.core.properties.types.invoke
import maryk.core.properties.types.numeric.UInt32
import maryk.core.protobuf.WriteCache
import maryk.core.query.DefinitionsContext
import maryk.core.query.RequestContext
import maryk.core.query.changes.Change
import maryk.core.query.changes.IncMapAddition
import maryk.core.query.changes.IncMapChange
import maryk.core.query.changes.IncMapKeyAdditions
import maryk.core.query.changes.IndexChange
import maryk.core.query.changes.ListChange
import maryk.core.query.changes.MultiTypeChange
import maryk.core.query.changes.ObjectCreate
import maryk.core.query.changes.ObjectSoftDeleteChange
import maryk.core.query.changes.SetChange
import maryk.core.query.changes.change
import maryk.core.query.filters.Equals
import maryk.core.query.orders.ascending
import maryk.core.query.orders.descending
import maryk.core.query.pairs.with
import maryk.core.query.requests.add
import maryk.core.query.requests.change
import maryk.core.query.requests.delete
import maryk.core.query.requests.get
import maryk.core.query.requests.scan
import maryk.core.query.responses.ChangeResponse
import maryk.core.query.responses.UpdateResponse
import maryk.core.query.responses.statuses.AddSuccess
import maryk.core.query.responses.statuses.ChangeSuccess
import maryk.core.query.responses.statuses.ServerFail
import maryk.core.query.responses.statuses.ValidationFail
import maryk.core.query.responses.updates.ChangeUpdate
import maryk.datastore.shared.IsDataStore
import maryk.json.JsonWriter
import maryk.test.models.MarykTypeEnum
import maryk.test.models.MarykTypeEnum.T1
import maryk.test.models.MarykTypeEnum.T2
import maryk.test.models.MarykTypeEnum.T4
import maryk.test.models.MarykTypeEnum.T5
import maryk.test.models.MarykTypeEnum.T6

object ValueIntegrityRecord : RootDataModel<ValueIntegrityRecord>(
    indexes = { listOf(ValueIntegrityRecord.label.ref()) },
) {
    val label by string(1u)
    val text by string(2u)
    val entries by map(3u, keyDefinition = StringDefinition(), valueDefinition = StringDefinition(minSize = 3u))
    val items by list(4u, valueDefinition = StringDefinition(), required = false)
    val members by set(5u, valueDefinition = StringDefinition(), required = false)
    val detail by embed(6u, dataModel = { StatefulIntegrityDetails }, required = false)
    val typed by multiType(7u, typeEnum = MarykTypeEnum, typeIsFinal = false, required = false)
    val fixedType by multiType(8u, typeEnum = MarykTypeEnum, required = false)
    val fixedValue by multiType(9u, typeEnum = MarykTypeEnum, final = true, required = false)
    val fixedText by string(10u, final = true, required = false)
    val fixedList by list(11u, valueDefinition = StringDefinition(), final = true, required = false)
    val fixedSet by set(12u, valueDefinition = StringDefinition(), final = true, required = false)
    val fixedMap by incrementingMap(13u, keyNumberDescriptor = UInt32, valueDefinition = StringDefinition(), final = true, required = false)
    val fixedBranch by embed(17u, dataModel = { AnonymousCollectionChild }, final = true, required = false)
    val fixedListEntries by map(18u, keyDefinition = StringDefinition(), valueDefinition = ListDefinition(valueDefinition = StringDefinition()), final = true, required = false)
    val fixedSetEntries by map(19u, keyDefinition = StringDefinition(), valueDefinition = SetDefinition(valueDefinition = StringDefinition()), final = true, required = false)
    val allocatedText by incrementingMap(14u, keyNumberDescriptor = UInt32, valueDefinition = StringDefinition(minSize = 3u), required = false)
    val allocatedLists by incrementingMap(15u, keyNumberDescriptor = UInt32,
        valueDefinition = ListDefinition(valueDefinition = StringDefinition(minSize = 3u)), required = false)
    val allocatedSets by incrementingMap(16u, keyNumberDescriptor = UInt32,
        valueDefinition = SetDefinition(valueDefinition = StringDefinition(minSize = 3u)), required = false)

}

object FixedKeyIntegrityRecord : RootDataModel<FixedKeyIntegrityRecord>(
    keyDefinition = { FixedKeyIntegrityRecord.id.ref() },
) {
    val id by number(1u, type = UInt32, final = true)
}

val validatedIntegrityModels = mapOf(1u to ValueIntegrityRecord, 2u to FixedKeyIntegrityRecord, 3u to AnonymousCollectionRecord)

suspend fun assertUnicodeWriteIntegrity(store: IsDataStore) {
    val model = ValueIntegrityRecord
    val key = model.key(ByteArray(model.Meta.keyByteSize))
    val invalid = listOf(0xD800.toChar().toString(), 0xDC00.toChar().toString(), "before" + 0xD800.toChar() + "after")
    for (text in invalid) {
        assertIs<ValidationFail<ValueIntegrityRecord>>(store.execute(model.add(key to model.create {
            label with "unchanged"
            this.text with text
            entries with emptyMap<String, String>()
        })).statuses.single())
        assertEquals(emptyList(), store.execute(model.get(key)).values)
    }
    val values = model.create {
        label with "unchanged"
        text with "valid\u0000🚀\uFFFD"
        entries with emptyMap<String, String>()
    }
    val added = assertIs<AddSuccess<ValueIntegrityRecord>>(store.execute(model.add(key to values)).statuses.single())
    for (text in invalid) {
        val response = store.execute(model.change(key.change(Change(
            model.ref { label } with "must roll back",
            model.ref { this.text } with text,
        ))))
        assertIs<ValidationFail<ValueIntegrityRecord>>(response.statuses.single())
        assertValidationResponseCanTravel(response)
        val current = store.execute(model.get(key)).values.single()
        assertEquals(values, current.values)
        assertEquals(added.version, current.lastVersion)
        assertEquals(emptyList(), store.execute(model.scan(where = Equals(model.ref { label } with "must roll back"))).values)
    }
    val invalidText = invalid.first()
    val complexChanges = listOf(
        Change(model.ref { entries } with mapOf("valid key" to invalidText)),
        Change(model.ref { items } with listOf(invalidText)),
        Change(model.ref { members } with setOf(invalidText)),
        Change(model.ref { detail } with StatefulIntegrityDetails.create { note with invalidText }),
    )
    for (change in complexChanges) {
        val response = store.execute(model.change(key.change(
            Change(model.ref { label } with "must roll back"), change,
        )))
        assertIs<ValidationFail<ValueIntegrityRecord>>(response.statuses.single(), response.toString())
        assertValidationResponseCanTravel(response)
        val current = store.execute(model.get(key)).values.single()
        assertEquals(values, current.values)
        assertEquals(added.version, current.lastVersion)
        assertEquals(emptyList(), store.execute(model.scan(where = Equals(model.ref { label } with "must roll back"))).values)
    }

}

suspend fun assertMalformedKeyDiagnosticsCanTravel(store: IsDataStore) {
    val model = ValueIntegrityRecord
    val key = assertIs<AddSuccess<ValueIntegrityRecord>>(store.execute(model.add(model.create {
        label with "unchanged"
        text with "valid"
        entries with emptyMap<String, String>()
    })).statuses.single()).key
    val response = store.execute(model.change(key.change(Change(
        model.ref { entries } with mapOf(0xD800.toChar().toString() to "x"),
    ))))
    assertIs<ValidationFail<ValueIntegrityRecord>>(response.statuses.single())
    assertValidationResponseCanTravel(response)
    assertEquals(emptyMap(), store.execute(model.get(key)).values.single().values { entries })
}

suspend fun assertTypedValuesRespectDefinitions(store: IsDataStore) {
    val model = ValueIntegrityRecord
    val original = model.create {
        label with "original"
        text with "valid"
        entries with emptyMap<String, String>()
        typed with T2(5)
        fixedType with T1("original")
        fixedValue with T1("original")
    }
    val added = assertIs<AddSuccess<ValueIntegrityRecord>>(store.execute(model.add(original)).statuses.single())
    val invalid = listOf(
        Change(model.ref { typed } with T2(2001)),
        Change(model.ref { typed } with T1("invalid&")),
        Change(model.ref { typed } with T4(listOf("invalid&"))),
        Change(model.ref { typed } with T5(setOf("invalid&"))),
        Change(model.ref { typed } with T6(mapOf(1u to "invalid&"))),
        Change(model.ref { typed } with T1(0xD800.toChar().toString())),
        Change(model.ref { fixedType } with T2(1)),
        Change(model.ref { fixedValue } with T1("changed")),
    )
    for (deleted in listOf(false, true)) {
        if (deleted) store.execute(model.delete(added.key))
        val expectedVersion = store.execute(model.get(added.key, filterSoftDeleted = false)).values.single().lastVersion
        for (change in invalid) {
            val response = store.execute(model.change(added.key.change(
                Change(model.ref { label } with "must roll back"), change,
            )))
            assertIs<ValidationFail<ValueIntegrityRecord>>(response.statuses.single(), response.toString())
            assertValidationResponseCanTravel(response)
            val current = store.execute(model.get(added.key, filterSoftDeleted = false)).values.single()
            assertEquals(original, current.values)
            assertEquals(expectedVersion, current.lastVersion)
            assertEquals(deleted, current.isDeleted)
            if (store.keepAllVersions) {
                assertEquals(original, store.execute(model.get(added.key, toVersion = added.version, filterSoftDeleted = false)).values.single().values)
            }
            assertEquals(emptyList(), store.execute(model.scan(where = Equals(model.ref { label } with "must roll back"))).values)
        }
    }
    assertIs<ChangeSuccess<ValueIntegrityRecord>>(store.execute(model.change(added.key.change(
        Change(model.ref { typed } with T2(2000)), ObjectSoftDeleteChange(false),
    ))).statuses.single())
    assertEquals(T2(2000), store.execute(model.get(added.key)).values.single().values { typed })
}

@Suppress("UNCHECKED_CAST")
suspend fun assertImmutableWriteIntegrity(store: IsDataStore) {
    val model = ValueIntegrityRecord
    val original = model.create {
        label with "original"
        text with "valid"
        entries with emptyMap<String, String>()
        fixedText with "original"
        fixedList with listOf("original")
        fixedSet with setOf("original")
        fixedMap with mapOf(1u to "original")
        fixedBranch with AnonymousCollectionChild.create {
            items with listOf("original")
            members with setOf("original")
            allocated with emptyMap<UInt, String>()
        }
        fixedListEntries with mapOf("entry" to listOf("original"))
        fixedSetEntries with mapOf("entry" to setOf("original"))
    }
    val added = assertIs<AddSuccess<ValueIntegrityRecord>>(store.execute(model.add(original)).statuses.single())
    val nestedList = model.getPropertyReferenceByName("fixedListEntries.@entry") as IsPropertyReference<List<String>, IsListDefinition<String, IsPropertyContext>, *>
    val invalid = listOf(
        Change(model.ref { fixedList at 0u } with "changed"),
        Change(model.ref { fixedList at 0u } with null),
        Change(model.ref { fixedSet item "original" } with null),
        Change(model.ref { fixedMap at 1u } with "changed"),
        Change(model.ref { fixedMap at 1u } with null),
        Change(model.ref { fixedMap at 2u } with "added"),
        Change(model.ref { fixedBranch { items at 0u } } with "changed"),
        Change(model.ref { fixedBranch { items } } with listOf("changed")),
        ListChange(model.ref { fixedBranch { items } }.change(addValuesToEnd = listOf("changed"))),
        SetChange(model.ref { fixedBranch { members } }.change(addValues = setOf("changed"))),
        IncMapChange(model.ref { fixedBranch { allocated } }.change(addValues = listOf("changed"))),
        ListChange(nestedList.change(addValuesToEnd = listOf("changed"))),
        SetChange(model.ref { fixedSetEntries at "entry" }.change(addValues = setOf("changed"))),
        Change(nestedList with listOf("changed")),
        Change(model.ref { fixedText } with "changed"),
        Change(model.ref { fixedText } with null),
        ListChange(model.ref { fixedList }.change(addValuesToEnd = listOf("changed"))),
        SetChange(model.ref { fixedSet }.change(addValues = setOf("changed"))),
        IncMapChange(model.ref { fixedMap }.change(addValues = listOf("changed"))),
    )
    for (change in invalid) {
        val response = store.execute(model.change(added.key.change(
            Change(model.ref { label } with "must roll back"), change,
        )))
        assertIs<ValidationFail<ValueIntegrityRecord>>(response.statuses.single(), response.toString())
        assertValidationResponseCanTravel(response)
        val current = store.execute(model.get(added.key)).values.single()
        assertEquals(original, current.values)
        assertEquals(added.version, current.lastVersion)
        if (store.keepAllVersions) {
            assertEquals(original, store.execute(model.get(added.key, toVersion = added.version)).values.single().values)
        }
    }
    for (unsupported in listOf(
        IncMapAddition(IncMapKeyAdditions(model.ref { fixedMap }, addedKeys = listOf(2u), addedValues = listOf("bypass"))),
        IndexChange(emptyList()),
        MultiTypeChange(),
        ObjectCreate,
    )) {
        val rejected = store.execute(model.change(added.key.change(
            Change(model.ref { label } with "must roll back"), unsupported,
        )))
        assertIs<ServerFail<ValueIntegrityRecord>>(rejected.statuses.single(), rejected.toString())
        val unchanged = store.execute(model.get(added.key)).values.single()
        assertEquals(original, unchanged.values)
        assertEquals(added.version, unchanged.lastVersion)
        assertEquals(emptyList(), store.execute(model.scan(where = Equals(model.ref { label } with "must roll back"))).values)
    }
    val noOp = assertIs<ChangeSuccess<ValueIntegrityRecord>>(store.execute(model.change(added.key.change(
        Change(model.ref { fixedText } with "original"),
        Change(model.ref { fixedList at 0u } with "original", model.ref { fixedMap at 1u } with "original"),
        ListChange(model.ref { fixedBranch { items } }.change(addValuesToEnd = emptyList())),
        SetChange(model.ref { fixedBranch { members } }.change(addValues = setOf("original"))),
        IncMapChange(model.ref { fixedBranch { allocated } }.change(addValues = emptyList())),
        ListChange(model.ref { fixedList }.change(addValuesToEnd = emptyList())),
        SetChange(model.ref { fixedSet }.change(addValues = setOf("original"))),
        IncMapChange(model.ref { fixedMap }.change(addValues = emptyList())),
    ))).statuses.single())
    assertEquals(added.version, noOp.version)
    assertEquals(original, store.execute(model.get(added.key)).values.single().values)

    val keyed = FixedKeyIntegrityRecord
    val keyAdded = assertIs<AddSuccess<FixedKeyIntegrityRecord>>(store.execute(keyed.add(keyed.create { id with 1u })).statuses.single())
    assertIs<ValidationFail<FixedKeyIntegrityRecord>>(store.execute(keyed.change(keyAdded.key.change(
        Change(keyed.ref { id } with 2u),
    ))).statuses.single())
    val current = store.execute(keyed.get(keyAdded.key)).values.single()
    assertEquals(1u, current.values { id })
    assertEquals(keyAdded.key, keyed.key(current.values))
    val sameKey = assertIs<ChangeSuccess<FixedKeyIntegrityRecord>>(store.execute(keyed.change(keyAdded.key.change(
        Change(keyed.ref { id } with 1u),
    ))).statuses.single())
    assertEquals(keyAdded.version, sameKey.version)

    val fresh = assertIs<AddSuccess<ValueIntegrityRecord>>(store.execute(model.add(model.create {
        label with "uninitialized"
        text with "valid"
        entries with emptyMap<String, String>()
    })).statuses.single())
    assertIs<ChangeSuccess<ValueIntegrityRecord>>(store.execute(model.change(fresh.key.change(
        ListChange(model.ref { fixedList }.change(addValuesToEnd = listOf("initial"))),
        SetChange(model.ref { fixedSet }.change(addValues = setOf("initial"))),
        IncMapChange(model.ref { fixedMap }.change(addValues = listOf("initial"))),
        Change(
            nestedList with listOf("initial"),
            model.ref { fixedBranch } with AnonymousCollectionChild.create {
                items with listOf("initial")
                members with setOf("initial")
            },
        ),
    ))).statuses.single())
    val initialized = store.execute(model.get(fresh.key)).values.single().values
    assertEquals(listOf("initial"), initialized { fixedList })
    assertEquals(setOf("initial"), initialized { fixedSet })
    assertEquals(mapOf(1u to "initial"), initialized { fixedMap })

}

suspend fun assertIndexBoundsHandleShortRows(store: IsDataStore) {
    val model = ValueIntegrityRecord
    val labels = listOf("", "a", "original", "must roll back", "must roll back and more")
    val keys = labels.map { label ->
        assertIs<AddSuccess<ValueIntegrityRecord>>(store.execute(model.add(model.create {
            this.label with label
            text with "valid"
            entries with emptyMap<String, String>()
        })).statuses.single()).key
    }
    for ((index, label) in labels.withIndex()) for (descending in listOf(false, true)) {
        val order = if (descending) model.ref { this.label }.descending() else model.ref { this.label }.ascending()
        assertEquals(listOf(keys[index]), store.execute(model.scan(
            where = Equals(model.ref { this.label } with label), order = order,
        )).values.map { it.key })
    }
    assertEquals(emptyList(), store.execute(model.scan(where = Equals(model.ref { label } with "must roll back but absent"))).values)
}

suspend fun assertAllocationErrorsRemainSerializable(store: IsDataStore) {
    val model = ValueIntegrityRecord
    val original = model.create {
        label with "original"
        text with "valid"
        entries with emptyMap<String, String>()
    }
    val added = assertIs<AddSuccess<ValueIntegrityRecord>>(store.execute(model.add(original)).statuses.single())
    val invalid = listOf(
        IncMapChange(model.ref { allocatedText }.change(addValues = listOf("x"))),
        IncMapChange(model.ref { allocatedLists }.change(addValues = listOf(listOf("x")))),
        IncMapChange(model.ref { allocatedSets }.change(addValues = listOf(setOf("x")))),
    )
    for (change in invalid) {
        val response = store.execute(model.change(added.key.change(
            Change(model.ref { label } with "must roll back"), change,
        )))
        assertIs<ValidationFail<ValueIntegrityRecord>>(response.statuses.single(), response.toString())
        assertValidationResponseCanTravel(response)
        val current = store.execute(model.get(added.key)).values.single()
        assertEquals(original, current.values)
        assertEquals(added.version, current.lastVersion)
        if (store.keepAllVersions) {
            assertEquals(original, store.execute(model.get(added.key, toVersion = added.version)).values.single().values)
        }
    }
    assertIs<ChangeSuccess<ValueIntegrityRecord>>(store.execute(model.change(added.key.change(IncMapChange(
        model.ref { allocatedText }.change(addValues = listOf("valid")),
        model.ref { allocatedLists }.change(addValues = listOf(listOf("valid"))),
        model.ref { allocatedSets }.change(addValues = listOf(setOf("valid"))),
    )))).statuses.single())
    val current = store.execute(model.get(added.key)).values.single().values
    assertEquals(mapOf(1u to "valid"), current { allocatedText })
    assertEquals(mapOf(1u to listOf("valid")), current { allocatedLists })
    assertEquals(mapOf(1u to setOf("valid")), current { allocatedSets })
}

private fun assertValidationResponseCanTravel(response: ChangeResponse<ValueIntegrityRecord>) {
    fun context() = RequestContext(DefinitionsContext(mutableMapOf(
        ValueIntegrityRecord.Meta.name to DataModelReference(ValueIntegrityRecord),
    )))
    val json = StringBuilder()
    ChangeResponse.Serializer.writeObjectAsJson(response, JsonWriter(writer = { json.append(it) }), context())
    val decodedJson = ChangeResponse(ChangeResponse.Serializer.readJson(json.toString(), context()))
    assertIs<ValidationFail<*>>(decodedJson.statuses.single())
    val cache = WriteCache()
    val size = ChangeResponse.Serializer.calculateObjectProtoBufLength(response, cache, context())
    val bytes = ByteArray(size)
    var index = 0
    ChangeResponse.Serializer.writeObjectProtoBuf(response, cache, { bytes[index++] = it }, context())
    index = 0
    val decodedProto = ChangeResponse(ChangeResponse.Serializer.readProtoBuf(size, { bytes[index++] }, context()))
    assertIs<ValidationFail<*>>(decodedProto.statuses.single())
}

/** Resolved replication keys initialize an absent map and advance subsequent local allocation. */
suspend fun assertAllocationReplayCreatesMissingMaps(store: IsDataStore) {
    val model = ValueIntegrityRecord
    val original = model.create {
        label with "original"
        text with "valid"
        entries with emptyMap<String, String>()
    }
    val added = assertIs<AddSuccess<ValueIntegrityRecord>>(store.execute(model.add(original)).statuses.single())
    val explicitWithoutParent = store.execute(model.change(added.key.change(
        Change(model.ref { allocatedText at 1u } with "explicit"),
    )))
    assertTrue(explicitWithoutParent.statuses.single() is ServerFail<*> || explicitWithoutParent.statuses.single() is ValidationFail<*>)
    assertEquals(original, store.execute(model.get(added.key)).values.single().values)
    val replicatedVersion = added.version + 10uL
    val replay = store.processUpdate(UpdateResponse(model, ChangeUpdate(added.key, replicatedVersion, 0, listOf(
        Change(model.ref { allocatedText at 1u } with "replicated"),
    ))))
    assertIs<ChangeSuccess<ValueIntegrityRecord>>(assertIs<ChangeResponse<ValueIntegrityRecord>>(replay.result).statuses.single())
    val replicated = store.execute(model.get(added.key)).values.single()
    assertEquals(mapOf(1u to "replicated"), replicated.values { allocatedText })
    assertEquals(replicatedVersion, replicated.lastVersion)
    val local = assertIs<ChangeSuccess<ValueIntegrityRecord>>(store.execute(model.change(added.key.change(
        IncMapChange(model.ref { allocatedText }.change(addValues = listOf("local"))),
    ))).statuses.single())
    assertEquals(mapOf(1u to "replicated", 2u to "local"), store.execute(model.get(added.key)).values.single().values { allocatedText })
    if (store.keepAllVersions) {
        assertEquals(original, store.execute(model.get(added.key, toVersion = added.version)).values.single().values)
        assertEquals(replicated.values, store.execute(model.get(added.key, toVersion = replicatedVersion)).values.single().values)
        assertEquals(local.version, store.execute(model.get(added.key)).values.single().lastVersion)
    }
}

/** Index-only scalar type references must never replace the parent's tagged representation. */
suspend fun assertSimpleTypedReferenceWriteIntegrity(store: IsDataStore) {
    val model = ValueIntegrityRecord
    val original = model.create {
        label with "original"
        text with "valid"
        entries with emptyMap<String, String>()
        typed with T2(5)
    }
    val added = assertIs<AddSuccess<ValueIntegrityRecord>>(store.execute(model.add(original)).statuses.single())
    for (change in listOf(
        Change(model.ref { typed simpleAtType T2 } with 6),
        Change(model.ref { typed simpleAtType T2 } with null),
    )) {
        val response = store.execute(model.change(added.key.change(Change(model.ref { label } with "must roll back"), change)))
        assertIs<ServerFail<ValueIntegrityRecord>>(response.statuses.single(), response.toString())
        val current = store.execute(model.get(added.key)).values.single()
        assertEquals(original, current.values)
        assertEquals(added.version, current.lastVersion)
    }
    assertIs<ChangeSuccess<ValueIntegrityRecord>>(store.execute(model.change(added.key.change(Change(model.ref { typed } with T2(6))))).statuses.single())
    assertEquals(T2(6), store.execute(model.get(added.key)).values.single().values { typed })
    if (store.keepAllVersions) assertEquals(original, store.execute(model.get(added.key, toVersion = added.version)).values.single().values)
}

/** The value stored at a set member qualifier must retain that qualifier's identity. */
suspend fun assertSetItemWriteIntegrity(store: IsDataStore) {
    val model = ValueIntegrityRecord
    val original = model.create {
        label with "original"
        text with "valid"
        entries with emptyMap<String, String>()
        members with setOf("original")
    }
    val added = assertIs<AddSuccess<ValueIntegrityRecord>>(store.execute(model.add(original)).statuses.single())
    val member = model.ref { members item "original" }
    for (change in listOf(
        Change(member with "changed"),
        Change(model.ref { members item "absent" } with "absent"),
    )) {
        val response = store.execute(model.change(added.key.change(Change(model.ref { label } with "must roll back"), change)))
        assertIs<ServerFail<ValueIntegrityRecord>>(response.statuses.single(), response.toString())
        val current = store.execute(model.get(added.key)).values.single()
        assertEquals(original, current.values)
        assertEquals(added.version, current.lastVersion)
    }
    val unchanged = assertIs<ChangeSuccess<ValueIntegrityRecord>>(store.execute(model.change(added.key.change(Change(member with "original")))).statuses.single())
    assertEquals(added.version, unchanged.version)
    assertIs<ChangeSuccess<ValueIntegrityRecord>>(store.execute(model.change(added.key.change(Change(member with null)))).statuses.single())
    assertEquals(emptySet(), store.execute(model.get(added.key)).values.single().values { members })
    if (store.keepAllVersions) assertEquals(original, store.execute(model.get(added.key, toVersion = added.version)).values.single().values)
}
