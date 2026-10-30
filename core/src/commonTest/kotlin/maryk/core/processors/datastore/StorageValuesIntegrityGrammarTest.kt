package maryk.core.processors.datastore

import maryk.checkJsonConversion
import maryk.checkYamlConversion
import maryk.core.models.RootDataModel
import maryk.core.models.fromChanges
import maryk.core.models.graph
import maryk.core.properties.IsPropertyContext
import maryk.core.properties.definitions.EmbeddedValuesDefinition
import maryk.core.properties.definitions.IsChangeableValueDefinition
import maryk.core.properties.definitions.IsPropertyDefinition
import maryk.core.properties.definitions.IsSimpleValueDefinition
import maryk.core.properties.definitions.ListDefinition
import maryk.core.properties.definitions.MapDefinition
import maryk.core.properties.definitions.MultiTypeDefinition
import maryk.core.properties.definitions.NumberDefinition
import maryk.core.properties.definitions.SetDefinition
import maryk.core.properties.definitions.StringDefinition
import maryk.core.properties.definitions.embed
import maryk.core.properties.definitions.incrementingMap
import maryk.core.properties.definitions.list
import maryk.core.properties.definitions.map
import maryk.core.properties.definitions.multiType
import maryk.core.properties.graph.RootPropRefGraph
import maryk.core.properties.references.IsPropertyReference
import maryk.core.properties.types.TypedValue
import maryk.core.properties.types.Key
import maryk.core.properties.types.invoke
import maryk.core.properties.types.numeric.UInt32
import maryk.core.protobuf.WriteCache
import maryk.core.query.RequestContext
import maryk.core.query.ValuesWithMetaData
import maryk.core.query.changes.Change
import maryk.core.query.changes.DataObjectVersionedChange
import maryk.core.query.changes.ListChange
import maryk.core.query.changes.VersionedChanges
import maryk.core.query.changes.ListValueChanges
import maryk.core.query.pairs.ReferenceValuePair
import maryk.core.query.pairs.ReferenceNullPair
import maryk.core.query.pairs.with
import maryk.core.query.responses.updates.ChangeUpdate
import maryk.core.query.responses.updates.processUpdateResponse
import maryk.core.values.Values
import maryk.core.yaml.MarykYamlReader
import maryk.lib.exceptions.ParseException
import maryk.test.models.EmbeddedMarykModel
import maryk.test.models.MarykTypeEnum
import maryk.test.models.SimpleMarykTypeEnum.S1
import maryk.test.models.SimpleMarykTypeEnum.S3
import maryk.yaml.YamlWriter
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFails
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

private object IntegrityGrammarModel : RootDataModel<IntegrityGrammarModel>() {
    val embedded by embed(1u, dataModel = { EmbeddedMarykModel })
    val embeddedMap by map(2u, keyDefinition = StringDefinition(), valueDefinition = EmbeddedValuesDefinition(dataModel = { EmbeddedMarykModel }))
    val embeddedList by list(3u, valueDefinition = EmbeddedValuesDefinition(dataModel = { EmbeddedMarykModel }))
    val typed by multiType(4u, typeEnum = MarykTypeEnum)
    val typedMap by map(5u, keyDefinition = StringDefinition(), valueDefinition = MultiTypeDefinition(typeEnum = MarykTypeEnum))
    val typedList by list(6u, valueDefinition = MultiTypeDefinition(typeEnum = MarykTypeEnum))
    val lists by map(7u, keyDefinition = StringDefinition(), valueDefinition = ListDefinition(valueDefinition = StringDefinition()))
    val sets by map(8u, keyDefinition = StringDefinition(), valueDefinition = SetDefinition(valueDefinition = StringDefinition()))
    val maps by map(9u, keyDefinition = StringDefinition(), valueDefinition = MapDefinition(keyDefinition = StringDefinition(), valueDefinition = StringDefinition()))
    val nested by map(10u, keyDefinition = StringDefinition(), valueDefinition = MapDefinition(keyDefinition = NumberDefinition(type = UInt32), valueDefinition = EmbeddedValuesDefinition(dataModel = { EmbeddedMarykModel })))
    val incEmbedded by incrementingMap(11u, keyNumberDescriptor = UInt32, valueDefinition = EmbeddedValuesDefinition(dataModel = { EmbeddedMarykModel }))
    val incTyped by incrementingMap(12u, keyNumberDescriptor = UInt32, valueDefinition = MultiTypeDefinition(typeEnum = MarykTypeEnum))
}

/** A bounded grammar, deliberately shared by transport, storage, references and creation replay. */
class StorageValuesIntegrityGrammarTest {
    private fun embeddedCases(): List<Values<EmbeddedMarykModel>> {
        val empty = EmbeddedMarykModel.create(setDefaults = false) {}
        return listOf(
            empty,
            EmbeddedMarykModel.create(setDefaults = false) { value with "" },
            EmbeddedMarykModel.create(setDefaults = false) { value with "a.\\@#*\u0000\uD83D\uDE42" },
            EmbeddedMarykModel.create(setDefaults = false) { model with empty },
            EmbeddedMarykModel.create(setDefaults = false) { model with { model with empty } },
            EmbeddedMarykModel.create(setDefaults = false) { value with "parent"; model with { value with "child"; model with empty } },
        )
    }

    private fun typedCases(): List<TypedValue<MarykTypeEnum<out Any>, Any>> = listOf(
        MarykTypeEnum.T1(""), MarykTypeEnum.T1("a.\\@#*\u0000\uD83D\uDE42"), MarykTypeEnum.T2(Int.MIN_VALUE), MarykTypeEnum.T2(Int.MAX_VALUE),
        *embeddedCases().map { MarykTypeEnum.T3(it) }.toTypedArray(),
        MarykTypeEnum.T4(emptyList()), MarykTypeEnum.T4(listOf("")), MarykTypeEnum.T4(listOf("same", "same")),
        MarykTypeEnum.T5(emptySet()), MarykTypeEnum.T5(setOf("", "a.\\@#*\u0000\uD83D\uDE42")),
        MarykTypeEnum.T6(emptyMap()), MarykTypeEnum.T6(mapOf(0u to "", UInt.MAX_VALUE to "last")),
        MarykTypeEnum.T7(S1("")), *embeddedCases().map { MarykTypeEnum.T7(S3(it)) }.toTypedArray(),
    )

    @Test
    fun recursiveEmbeddedGrammarRetainsPresenceAcrossAllPaths() {
        for ((case, embedded) in embeddedCases().withIndex()) {
            for ((path, values) in listOf(
                "direct" to IntegrityGrammarModel.create(setDefaults = false) { this.embedded with embedded },
                "map" to IntegrityGrammarModel.create(setDefaults = false) { embeddedMap with linkedMapOf("" to embedded, "next" to embedded) },
                "list" to IntegrityGrammarModel.create(setDefaults = false) { embeddedList with listOf(embedded, embedded) },
                "nested map" to IntegrityGrammarModel.create(setDefaults = false) { nested with mapOf("" to mapOf(0u to embedded, UInt.MAX_VALUE to embedded)) },
            )) verifyAllPaths(values, "$case $path")
        }
    }

    @Test
    fun typedGrammarRetainsEveryCaseInDirectMapAndListPositions() {
        for ((case, typed) in typedCases().withIndex()) {
            for ((path, values) in listOf(
                "direct" to IntegrityGrammarModel.create(setDefaults = false) { this.typed with typed },
                "map" to IntegrityGrammarModel.create(setDefaults = false) { typedMap with linkedMapOf("" to typed, "next" to typed) },
                "list" to IntegrityGrammarModel.create(setDefaults = false) { typedList with listOf(typed, typed) },
            )) verifyAllPaths(values, "$case $path")
        }
    }

    @Test
    fun incrementingMapsPreserveExplicitBoundaryKeysWithoutAllocatingThem() {
        for ((case, embedded) in embeddedCases().withIndex()) {
            verifyAllPaths(IntegrityGrammarModel.create(setDefaults = false) {
                incEmbedded with linkedMapOf(0u to embedded, UInt.MAX_VALUE to embedded)
            }, "incrementing embedded $case")
        }
        for ((case, typed) in typedCases().withIndex()) {
            verifyAllPaths(IntegrityGrammarModel.create(setDefaults = false) {
                incTyped with linkedMapOf(0u to typed, UInt.MAX_VALUE to typed)
            }, "incrementing typed $case")
        }
        verifyAllPaths(IntegrityGrammarModel.create(setDefaults = false) {
            incEmbedded with emptyMap(); incTyped with emptyMap()
        }, "empty incrementing maps")
    }

    @Test
    fun emptyAndNonemptyCollectionGrammarRetainsItsStructuralRows() {
        val strings = listOf(emptyList(), listOf(""), listOf("a", "a"), listOf("", "a.\\@#*\u0000\uD83D\uDE42"))
        for ((case, items) in strings.withIndex()) {
            verifyAllPaths(IntegrityGrammarModel.create(setDefaults = false) {
                lists with linkedMapOf("" to items, "next" to items.reversed())
                sets with linkedMapOf("" to items.toSet(), "next" to items.reversed().toSet())
                maps with linkedMapOf("" to items.associateWith { it }, "next" to items.reversed().associateWith { it })
            }, "collection $case")
        }
        verifyAllPaths(IntegrityGrammarModel.create(setDefaults = false) {
            embeddedMap with emptyMap(); embeddedList with emptyList(); typedMap with emptyMap(); typedList with emptyList()
            lists with emptyMap(); sets with emptyMap(); maps with emptyMap(); nested with emptyMap()
        }, "empty parents")
    }

    @Test
    fun creationHistoryRetainsEmptyEmbeddedAndCollectionStructures() {
        val empty = EmbeddedMarykModel.create(setDefaults = false) {}
        val cases = listOf(
            IntegrityGrammarModel.create(setDefaults = false) { embedded with empty },
            IntegrityGrammarModel.create(setDefaults = false) { embedded with { model with empty } },
            IntegrityGrammarModel.create(setDefaults = false) { embeddedMap with mapOf("" to empty) },
            IntegrityGrammarModel.create(setDefaults = false) { typed with MarykTypeEnum.T3(empty) },
            IntegrityGrammarModel.create(setDefaults = false) { typed with MarykTypeEnum.T7(S3(empty)) },
            IntegrityGrammarModel.create(setDefaults = false) { typed with MarykTypeEnum.T4(emptyList()) },
            IntegrityGrammarModel.create(setDefaults = false) { typed with MarykTypeEnum.T5(emptySet()) },
            IntegrityGrammarModel.create(setDefaults = false) { typed with MarykTypeEnum.T6(emptyMap()) },
            IntegrityGrammarModel.create(setDefaults = false) { embeddedMap with emptyMap(); embeddedList with emptyList(); typedMap with emptyMap(); typedList with emptyList() },
            IntegrityGrammarModel.create(setDefaults = false) { lists with mapOf("" to emptyList()); sets with mapOf("" to emptySet()); maps with mapOf("" to emptyMap()) },
        )
        for ((case, values) in cases.withIndex()) {
            val rows = rows(values)
            var index = -1
            val changes = IntegrityGrammarModel.readStorageToChanges(
                getQualifier = { handler -> rows.getOrNull(++index)?.let { row -> handler({ row.qualifier[it] }, row.qualifier.size); true } ?: false },
                select = null,
                creationVersion = 1uL,
                processValue = { _, _, handler -> handler(1uL, rows[index].value) },
            )
            assertEquals(values, IntegrityGrammarModel.fromChanges(null, changes.flatMap { it.changes }), "history $case")
        }
    }

    @Test
    fun ordinaryNestedChangesKeepTheOriginalValuesUnchanged() {
        for (embedded in embeddedCases()) {
            val original = IntegrityGrammarModel.create(setDefaults = false) {
                embeddedList with listOf(embedded, embedded)
                embeddedMap with mapOf("" to embedded, "next" to embedded)
            }
            val unchanged = transport(original)
            val listRef = IntegrityGrammarModel.getPropertyReferenceByName("embeddedList.@0.value")
            val mapRef = IntegrityGrammarModel.getPropertyReferenceByName("embeddedMap.@.value")
            @Suppress("UNCHECKED_CAST")
            val changed = original.change(Change(
                ReferenceValuePair(listRef as IsPropertyReference<String, IsChangeableValueDefinition<String, IsPropertyContext>, *>, "changed list"),
                ReferenceValuePair(mapRef as IsPropertyReference<String, IsChangeableValueDefinition<String, IsPropertyContext>, *>, "changed map"),
            ))
            assertEquals(unchanged, original)
            verifyAllPaths(changed, "changed embedded")
        }
    }

    @Test
    fun repeatedDeepTypedDeletionsDoNotMutateTheirOriginalOrSiblingWorkingCopies() {
        val embedded = EmbeddedMarykModel.create(setDefaults = false) {
            value with "outer"
            model with { value with "middle"; model with { value with "remove"; model with { value with "retained" } } }
        }
        val original = IntegrityGrammarModel.create(setDefaults = false) {
            embeddedMap with mapOf("" to embedded)
            typedMap with mapOf("" to MarykTypeEnum.T3(embedded))
            typedList with listOf(MarykTypeEnum.T3(embedded))
            incTyped with mapOf(0u to MarykTypeEnum.T3(embedded))
        }
        val frozen = transport(original)
        val pairs = listOf("embeddedMap.@.model.model.value", "typedMap.@.*T3.model.model.value",
            "typedList.@0.*T3.model.model.value", "incTyped.@0.*T3.model.model.value").map { name ->
            @Suppress("UNCHECKED_CAST")
            val reference = IntegrityGrammarModel.getPropertyReferenceByName(name) as IsPropertyReference<Any, IsChangeableValueDefinition<Any, IsPropertyContext>, *>
            ReferenceNullPair(reference)
        }
        val deletion = Change(pairs)
        val first = original.change(deletion)
        assertEquals(frozen, original, "first materialization must leave the source untouched")
        val second = original.change(deletion)
        assertEquals(frozen, original, "repeated materialization must leave the source untouched")
        assertEquals(first, second)
        for (pair in pairs) {
            assertNull(first[pair.reference])
            assertEquals("remove", original[pair.reference])
            @Suppress("UNCHECKED_CAST")
            val sibling = IntegrityGrammarModel.getPropertyReferenceByName(pair.reference.completeName.removeSuffix("value") + "model.value") as IsPropertyReference<Any, IsPropertyDefinition<Any>, Any>
            assertEquals("retained", first[sibling])
        }
        assertEquals(first, transport(first))
    }

    @Test
    fun deletedEmbeddedPresenceSuppressesLingeringChildrenAndProjectionDoesNotInventDefaults() {
        val original = IntegrityGrammarModel.create(setDefaults = false) {
            embedded with { value with "lingering"; model with {} }
            embeddedMap with mapOf("" to EmbeddedMarykModel.create(setDefaults = false) { value with "mapped" })
        }
        val rows = rows(original)
        val deleted = rows.map { if (it.qualifier.contentEquals(IntegrityGrammarModel.ref { embedded }.toStorageByteArray())) it.copy(value = null) else it }
        val read = read(deleted)
        assertNull(read { embedded })
        assertEquals(original { embeddedMap }, read { embeddedMap })
        val empty = IntegrityGrammarModel.create(setDefaults = false) { embedded with EmbeddedMarykModel.create(setDefaults = false) {} }
        assertEquals(empty, read(rows(empty), IntegrityGrammarModel.graph { listOf(embedded) }))
        assertEquals(0, read(rows(empty), IntegrityGrammarModel.graph { listOf(embeddedMap) }).size)
    }

    @Test
    fun typedCollectionMembersIgnoreRowsBelongingToAnEarlierCase() {
        val old = IntegrityGrammarModel.create(setDefaults = false) {
            typedMap with mapOf("" to MarykTypeEnum.T3(EmbeddedMarykModel.create(setDefaults = false) { value with "old map" }))
            typedList with listOf(MarykTypeEnum.T3(EmbeddedMarykModel.create(setDefaults = false) { value with "old list" }))
        }
        val current = IntegrityGrammarModel.create(setDefaults = false) {
            typedMap with mapOf("" to MarykTypeEnum.T4(emptyList()))
            typedList with listOf(MarykTypeEnum.T4(emptyList()))
        }
        val currentRows = rows(current)
        val obsoleteRows = rows(old).filter { oldRow -> currentRows.none { it.qualifier.contentEquals(oldRow.qualifier) } }
        assertEquals(current, read((currentRows + obsoleteRows).sortedBy { it.qualifier.toHexString() }))
    }

    @Test
    fun ordinaryChangesDoNotCreateMissingStructuresOrAppendOutOfRangeMembers() {
        val empty = IntegrityGrammarModel.create(setDefaults = false) {}
        val child = IntegrityGrammarModel.getPropertyReferenceByName("embeddedList.@0.value")
        @Suppress("UNCHECKED_CAST")
        val pair = ReferenceValuePair(child as IsPropertyReference<String, IsChangeableValueDefinition<String, IsPropertyContext>, *>, "value")
        assertFails { empty.change(Change(pair)) }
        val emptyList = IntegrityGrammarModel.create(setDefaults = false) { embeddedList with emptyList() }
        assertFails { emptyList.change(Change(pair)) }
        val sparse = IntegrityGrammarModel.getPropertyReferenceByName("embeddedList.@1.value")
        @Suppress("UNCHECKED_CAST")
        val sparsePair = ReferenceValuePair(sparse as IsPropertyReference<String, IsChangeableValueDefinition<String, IsPropertyContext>, *>, "value")
        assertFails { IntegrityGrammarModel.fromChanges(null, listOf(Change(sparsePair))) }
        assertEquals(empty, IntegrityGrammarModel.fromChanges(null, listOf(Change(
            IntegrityGrammarModel.ref { embeddedList }.with(null),
        ))))
    }

    @Test
    fun ordinaryListChangesCanBeTransportedAtEveryLegalNestedReference() {
        val original = IntegrityGrammarModel.create(setDefaults = false) {
            lists with mapOf("" to listOf("a"))
            typed with MarykTypeEnum.T4(listOf("a"))
            typedList with listOf(MarykTypeEnum.T4(listOf("a")))
        }
        for (name in listOf("lists.@", "typed.*T4", "typedList.@0.*T4")) {
            @Suppress("UNCHECKED_CAST")
            val reference = IntegrityGrammarModel.getPropertyReferenceByName(name) as IsPropertyReference<List<String>, IsPropertyDefinition<List<String>>, *>
            val change = ListChange(ListValueChanges(reference, deleteValues = listOf("a"), addValuesAtIndex = mapOf(0u to ""), addValuesToEnd = listOf("tail")))
            val transported = transportHistory(VersionedChanges(2uL, listOf(change)))
            assertEquals(listOf(change), transported.changes, name)
            assertEquals(original.change(change), original.change(transported.changes), name)
        }
    }

    @Test
    fun partialDeltaHistoriesKeepUnchangedEmbeddedSiblingsAtEveryPlacement() {
        val retained = EmbeddedMarykModel.create(setDefaults = false) { value with "retained sibling" }
        val before = EmbeddedMarykModel.create(setDefaults = false) { value with "before"; model with retained }
        val after = EmbeddedMarykModel.create(setDefaults = false) { value with "after"; model with retained }

        for ((case, states) in embeddedPlacements(before).zip(embeddedPlacements(after)).withIndex()) {
            val (previous, expected) = states
            val previousRows = rows(previous)
            val nextRows = rows(expected)
            val changed = nextRows.filter { next -> previousRows.none { it.qualifier.contentEquals(next.qualifier) && it.value == next.value } }
            val changedLeaf = changed.single()
            val presence = nextRows.first { it.value == Unit && it.qualifier.size < changedLeaf.qualifier.size && it.qualifier.indices.all { index -> it.qualifier[index] == changedLeaf.qualifier[index] } }
            val changes = deltaHistory((changed + presence).sortedBy { it.qualifier.toHexString() })
            assertEquals(expected, previous.change(changes.flatMap { it.changes }), "partial delta $case")
            assertEquals("retained sibling", retained { value })
        }
    }

    @Test
    fun clearingAndDeletingContainersSuppressRedundantChildTombstonesInDeltas() {
        val item = EmbeddedMarykModel.create(setDefaults = false) { value with "item"; model with { value with "nested" } }
        val states = listOf(
            IntegrityGrammarModel.create(setDefaults = false) { embeddedList with listOf(item, item) } to IntegrityGrammarModel.create(setDefaults = false) { embeddedList with emptyList() },
            IntegrityGrammarModel.create(setDefaults = false) { embeddedMap with mapOf("" to item, "next" to item) } to IntegrityGrammarModel.create(setDefaults = false) { embeddedMap with emptyMap() },
            IntegrityGrammarModel.create(setDefaults = false) { typedList with listOf(MarykTypeEnum.T3(item), MarykTypeEnum.T3(item)) } to IntegrityGrammarModel.create(setDefaults = false) { typedList with emptyList() },
            IntegrityGrammarModel.create(setDefaults = false) { lists with mapOf("" to listOf("first", "second")) } to IntegrityGrammarModel.create(setDefaults = false) { lists with mapOf("" to emptyList()) },
            IntegrityGrammarModel.create(setDefaults = false) { sets with mapOf("" to setOf("first", "second")) } to IntegrityGrammarModel.create(setDefaults = false) { sets with mapOf("" to emptySet()) },
            IntegrityGrammarModel.create(setDefaults = false) { maps with mapOf("" to mapOf("first" to "a", "second" to "b")) } to IntegrityGrammarModel.create(setDefaults = false) { maps with mapOf("" to emptyMap()) },
        )
        for ((case, states) in states.withIndex()) {
            val (previous, cleared) = states
            for ((operation, expected) in listOf("clear" to cleared, "delete" to IntegrityGrammarModel.create(setDefaults = false) {})) {
                val previousRows = rows(previous)
                val nextRows = rows(expected)
                val changed = nextRows.filter { next -> previousRows.none { it.qualifier.contentEquals(next.qualifier) && it.value == next.value } }
                val tombstones = previousRows.filter { old -> nextRows.none { it.qualifier.contentEquals(old.qualifier) } }.map { it.copy(value = null) }
                val changes = deltaHistory((changed + tombstones).sortedBy { it.qualifier.toHexString() })
                assertEquals(expected, previous.change(changes.flatMap { it.changes }), "$operation delta $case")
            }
        }
    }

    @Test
    fun embeddedPresenceDoesNotSuppressChildTombstonesWhenTheEmbeddedValueBecomesEmpty() {
        val before = EmbeddedMarykModel.create(setDefaults = false) { value with "remove"; model with { value with "remove nested" } }
        val empty = EmbeddedMarykModel.create(setDefaults = false) {}
        for ((case, states) in embeddedPlacements(before).zip(embeddedPlacements(empty)).withIndex()) {
            val (previous, expected) = states
            val previousRows = rows(previous)
            val nextRows = rows(expected)
            val tombstones = previousRows.filter { old -> nextRows.none { it.qualifier.contentEquals(old.qualifier) } }.map { it.copy(value = null) }
            val presence = nextRows.filter { it.value == Unit }
            val changes = deltaHistory((presence + tombstones).sortedBy { it.qualifier.toHexString() })
            assertEquals(expected, previous.change(changes.flatMap { it.changes }), "empty embedded delta $case")
        }
    }

    @Test
    fun snapshotBackedDeltasExplicitlyRecreateEmbeddedParents() {
        val nestedEmpty = EmbeddedMarykModel.create(setDefaults = false) { model with EmbeddedMarykModel.create(setDefaults = false) {} }
        val previous = IntegrityGrammarModel.create(setDefaults = false) {}
        for ((case, expected) in embeddedPlacements(nestedEmpty).withIndex()) {
            val changes = deltaHistory(rows(expected), expected)
            val replayed = try {
                previous.change(changes.flatMap { it.changes })
            } catch (error: Throwable) {
                throw IllegalStateException("resurrected embedded parent $case: $changes", error)
            }
            assertEquals(expected, replayed, "resurrected embedded parent $case")
        }
    }

    @Test
    fun listResizeDeltasCanBeAppliedToThePreviousValues() {
        fun placements(items: List<String>) = listOf(
            IntegrityGrammarModel.create(setDefaults = false) { typedList with items.map { MarykTypeEnum.T1(it) } },
            IntegrityGrammarModel.create(setDefaults = false) { embeddedList with items.map { EmbeddedMarykModel.create(setDefaults = false) { value with it } } },
            IntegrityGrammarModel.create(setDefaults = false) { lists with mapOf("" to items) },
            IntegrityGrammarModel.create(setDefaults = false) { typed with MarykTypeEnum.T4(items) },
            IntegrityGrammarModel.create(setDefaults = false) { typedMap with mapOf("" to MarykTypeEnum.T4(items)) },
            IntegrityGrammarModel.create(setDefaults = false) { typedList with listOf(MarykTypeEnum.T4(items)) },
        )
        val long = placements(listOf("first", "second", "third"))
        val short = placements(listOf("first"))
        val reordered = placements(listOf("third", "first"))
        val states = long.zip(short) + short.zip(long) + long.zip(reordered)
        for ((case, states) in states.withIndex()) {
            val (previous, expected) = states
            val oldRows = rows(previous)
            val newRows = rows(expected)
            val changed = newRows.filter { row -> oldRows.none { it.qualifier.contentEquals(row.qualifier) && it.value == row.value } }
            val removed = oldRows.filter { old -> newRows.none { it.qualifier.contentEquals(old.qualifier) } }.map { it.copy(value = null) }
            val changes = deltaHistory((changed + removed).sortedBy { it.qualifier.toHexString() }, expected)
            assertEquals(expected, previous.change(changes.flatMap { it.changes }), "list resize delta $case")
        }
    }

    @Test
    fun liveListItemRemovalRetainsOrdinaryShiftingRequestSemantics() {
        val first = MarykTypeEnum.T1("first")
        val second = MarykTypeEnum.T1("second")
        val third = MarykTypeEnum.T1("third")
        val previous = IntegrityGrammarModel.create(setDefaults = false) { typedList with listOf(first, second, third) }
        val expected = IntegrityGrammarModel.create(setDefaults = false) { typedList with listOf(second, third) }
        val key = Key<IntegrityGrammarModel>(ByteArray(IntegrityGrammarModel.Meta.keyByteSize))
        val original = ValuesWithMetaData(key, previous, 1uL, 1uL, false)
        val removal = Change(IntegrityGrammarModel.ref { typedList at 0u } with null)
        val result = processUpdateResponse(ChangeUpdate(key, 2uL, 0, listOf(removal)), listOf(original))
        assertEquals(expected, result.single().values)
        assertEquals(2uL, result.single().lastVersion)
        assertEquals(3, previous { typedList }?.size)
    }

    @Test
    fun nestedYamlMultiTypesRequireExactlyOneInnerValue() {
        val definition = MultiTypeDefinition(typeEnum = MarykTypeEnum)
        for (yaml in listOf("!T7(7) []", "!T7(7) [!S1(1) first, !S1(1) second]", "!T7(7) plain")) {
            val reader = MarykYamlReader(yaml)
            reader.nextToken()
            assertFailsWith<ParseException>(yaml) { definition.readJson(reader) }
        }
    }

    private fun embeddedPlacements(value: Values<EmbeddedMarykModel>) = listOf(
            IntegrityGrammarModel.create(setDefaults = false) { embedded with value },
            IntegrityGrammarModel.create(setDefaults = false) { embeddedMap with mapOf("" to value) },
            IntegrityGrammarModel.create(setDefaults = false) { embeddedList with listOf(value) },
            IntegrityGrammarModel.create(setDefaults = false) { typed with MarykTypeEnum.T3(value) },
            IntegrityGrammarModel.create(setDefaults = false) { typedMap with mapOf("" to MarykTypeEnum.T3(value)) },
            IntegrityGrammarModel.create(setDefaults = false) { typedList with listOf(MarykTypeEnum.T3(value)) },
            IntegrityGrammarModel.create(setDefaults = false) { incEmbedded with mapOf(0u to value) },
            IntegrityGrammarModel.create(setDefaults = false) { incTyped with mapOf(0u to MarykTypeEnum.T3(value)) },
        )

    private fun deltaHistory(rows: List<Row>, snapshot: Values<IntegrityGrammarModel>? = null): List<VersionedChanges> {
        var index = -1
        val qualifiers: ((((Int) -> Byte, Int) -> Unit) -> Boolean) = { handler ->
            rows.getOrNull(++index)?.let { row -> handler({ row.qualifier[it] }, row.qualifier.size); true } ?: false
        }
        val reader: ValueWithVersionReader = { _, _, handler -> handler(2uL, rows[index].value) }
        return (if (snapshot == null) IntegrityGrammarModel.readStorageToChanges(qualifiers, null, null, reader)
        else IntegrityGrammarModel.readStorageToChanges(qualifiers, null, null, { version ->
            assertEquals(2uL, version)
            snapshot
        }, reader)).map(::transportHistory)
    }

    private fun verifyAllPaths(values: Values<IntegrityGrammarModel>, message: String) {
        assertEquals(values, transport(values), "$message transport")
        assertEquals(values, IntegrityGrammarModel.fromChanges(null, values.toChanges().toList()), "$message creation replay")
        val rows = rows(values)
        assertEquals(rows.size, rows.map { it.qualifier.toHexString() }.toSet().size, "$message unique qualifiers")
        for (row in rows) {
            var offset = 0
            val reference = IntegrityGrammarModel.getPropertyReferenceByStorageBytes(row.qualifier.size, { row.qualifier[offset++] })
            assertEquals(row.qualifier.size, offset, "$message reference consumed")
            assertContentEquals(row.qualifier, reference.toStorageByteArray(), "$message reference storage")
            assertContentEquals(row.qualifier, IntegrityGrammarModel.getPropertyReferenceByName(reference.completeName).toStorageByteArray(), "$message reference name")
            val cache = WriteCache()
            val bytes = ByteArray(reference.calculateTransportByteLength(cache))
            offset = 0
            reference.writeTransportBytes(cache) { bytes[offset++] = it }
            assertEquals(bytes.size, offset)
            offset = 0
            val transported = IntegrityGrammarModel.getPropertyReferenceByBytes(bytes.size, { bytes[offset++] })
            assertEquals(bytes.size, offset)
            assertContentEquals(row.qualifier, transported.toStorageByteArray(), "$message reference transport")
        }
        assertEquals(values, read(rows), "$message storage readback")
        var rowIndex = -1
        val history = IntegrityGrammarModel.readStorageToChanges(
            getQualifier = { handler -> rows.getOrNull(++rowIndex)?.let { row -> handler({ row.qualifier[it] }, row.qualifier.size); true } ?: false },
            select = null,
            creationVersion = 1uL,
            processValue = { _, _, handler -> handler(1uL, rows[rowIndex].value) },
        )
        val transportedHistory = history.map(::transportHistory)
        assertEquals(history, transportedHistory, "$message history transport")
        val envelope = DataObjectVersionedChange(Key<IntegrityGrammarModel>(ByteArray(IntegrityGrammarModel.Meta.keyByteSize)), changes = transportedHistory)
        val context = RequestContext(emptyMap(), dataModel = IntegrityGrammarModel)
        val cache = WriteCache()
        val envelopeBytes = ByteArray(DataObjectVersionedChange.Serializer.calculateObjectProtoBufLength(envelope, cache, context))
        var envelopeOffset = 0
        DataObjectVersionedChange.Serializer.writeObjectProtoBuf(envelope, cache, { envelopeBytes[envelopeOffset++] = it }, context)
        assertEquals(envelopeBytes.size, envelopeOffset)
        envelopeOffset = 0
        val transportedEnvelope = DataObjectVersionedChange.Serializer.readProtoBuf(envelopeBytes.size, { envelopeBytes[envelopeOffset++] }, context).toDataObject()
        assertEquals(envelopeBytes.size, envelopeOffset)
        assertEquals(envelope, transportedEnvelope, "$message history envelope")
        assertEquals(values, IntegrityGrammarModel.fromChanges(null, transportedHistory.flatMap { it.changes }), "$message creation history replay")
        var snapshotRowIndex = -1
        val snapshotHistory = IntegrityGrammarModel.readStorageToChanges(
            getQualifier = { handler -> rows.getOrNull(++snapshotRowIndex)?.let { row -> handler({ row.qualifier[it] }, row.qualifier.size); true } ?: false },
            select = null,
            creationVersion = 1uL,
            readValuesAtVersion = { version -> assertEquals(1uL, version); values },
            processValue = { _, _, handler -> handler(1uL, rows[snapshotRowIndex].value) },
        ).map(::transportHistory)
        assertEquals(values, IntegrityGrammarModel.fromChanges(null, snapshotHistory.flatMap { it.changes }), "$message snapshot history replay")
    }

    private fun transport(values: Values<IntegrityGrammarModel>): Values<IntegrityGrammarModel> {
        val cache = WriteCache()
        val length = IntegrityGrammarModel.Serializer.calculateProtoBufLength(values, cache)
        val bytes = ByteArray(length)
        var offset = 0
        IntegrityGrammarModel.Serializer.writeProtoBuf(values, cache, { bytes[offset++] = it })
        assertEquals(length, offset)
        offset = 0
        return IntegrityGrammarModel.Serializer.readProtoBuf(length, { bytes[offset++] }).also { assertEquals(length, offset) }
    }

    private fun transportHistory(changes: VersionedChanges): VersionedChanges {
        checkJsonConversion(changes, VersionedChanges, { RequestContext(emptyMap(), dataModel = IntegrityGrammarModel) })
        try {
            checkYamlConversion(changes, VersionedChanges, { RequestContext(emptyMap(), dataModel = IntegrityGrammarModel) })
        } catch (error: Throwable) {
            val yaml = buildString {
                VersionedChanges.Serializer.writeObjectAsJson(changes, YamlWriter(::append), RequestContext(emptyMap(), dataModel = IntegrityGrammarModel))
            }
            throw IllegalStateException(yaml, error)
        }
        val context = RequestContext(emptyMap(), dataModel = IntegrityGrammarModel)
        val cache = WriteCache()
        val size = VersionedChanges.Serializer.calculateObjectProtoBufLength(changes, cache, context)
        val bytes = ByteArray(size)
        var offset = 0
        VersionedChanges.Serializer.writeObjectProtoBuf(changes, cache, { bytes[offset++] = it }, context)
        assertEquals(size, offset)
        offset = 0
        return VersionedChanges.Serializer.readProtoBuf(size, { bytes[offset++] }, context).toDataObject().also { assertEquals(size, offset) }
    }

    private data class Row(val qualifier: ByteArray, val value: Any?)

    private fun rows(values: Values<IntegrityGrammarModel>): List<Row> = buildList {
        values.writeToStorage { type, qualifier, definition, value ->
            val stored = if (type == StorageTypeEnum.Value) {
                @Suppress("UNCHECKED_CAST")
                val codec = definition as IsSimpleValueDefinition<Any, IsPropertyContext>
                val bytes = codec.toStorageBytes(value)
                assertEquals(codec.calculateStorageByteLength(value), bytes.size)
                var offset = 0
                codec.readStorageBytes(bytes.size) { bytes[offset++] }.also { assertEquals(bytes.size, offset) }
            } else value
            add(Row(qualifier, stored))
        }
    }.sortedBy { it.qualifier.toHexString() }

    private fun read(rows: List<Row>, select: RootPropRefGraph<IntegrityGrammarModel>? = null): Values<IntegrityGrammarModel> {
        var index = -1
        return IntegrityGrammarModel.readStorageToValues(
            getQualifier = { handler -> rows.getOrNull(++index)?.let { row -> handler({ row.qualifier[it] }, row.qualifier.size); true } ?: false },
            select = select,
            processValue = { _, _ -> rows[index].value },
        )
    }
}
