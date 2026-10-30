package maryk.core.properties.references

import maryk.core.exceptions.DefNotFoundException
import maryk.core.models.DataModel
import maryk.core.models.RootDataModel
import maryk.core.models.migration.toReleasedStorageByteArray
import maryk.core.processors.datastore.writeToStorage
import maryk.core.properties.IsPropertyContext
import maryk.core.properties.definitions.EmbeddedValuesDefinition
import maryk.core.properties.definitions.IsComparableDefinition
import maryk.core.properties.definitions.IsUsableInMultiType
import maryk.core.properties.definitions.NumberDefinition
import maryk.core.properties.definitions.ListDefinition
import maryk.core.properties.definitions.MapDefinition
import maryk.core.properties.definitions.MultiTypeDefinition
import maryk.core.properties.definitions.SetDefinition
import maryk.core.properties.definitions.StringDefinition
import maryk.core.properties.definitions.list
import maryk.core.properties.definitions.map
import maryk.core.properties.definitions.multiType
import maryk.core.properties.definitions.number
import maryk.core.properties.enum.IndexedEnumImpl
import maryk.core.properties.enum.MultiTypeEnum
import maryk.core.properties.enum.MultiTypeEnumDefinition
import maryk.core.properties.types.numeric.Float32
import maryk.core.properties.types.numeric.Float64
import maryk.core.properties.types.numeric.SInt32
import maryk.core.properties.types.invoke
import maryk.core.properties.types.TypedValue
import maryk.core.values.Values
import maryk.lib.exceptions.ParseException
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertTrue

private object StorageReferenceDetails : DataModel<StorageReferenceDetails>() {
    val double by number(1u, type = Float64, unique = true)
    val float by number(2u, type = Float32, unique = true)
}

private object StorageReferenceBranch : DataModel<StorageReferenceBranch>() {
    val children by map(1u, keyDefinition = StringDefinition(), valueDefinition = EmbeddedValuesDefinition(dataModel = { StorageReferenceDetails }))
    val entries by list(2u, valueDefinition = EmbeddedValuesDefinition(dataModel = { StorageReferenceDetails }))
}

private sealed class StorageReferenceType<T : Any>(
    index: UInt,
    override val definition: IsUsableInMultiType<T, IsPropertyContext>,
) : IndexedEnumImpl<StorageReferenceType<*>>(index), MultiTypeEnum<T> {
    data object Details : StorageReferenceType<Values<StorageReferenceDetails>>(1u, EmbeddedValuesDefinition(dataModel = { StorageReferenceDetails }))
    data object Branch : StorageReferenceType<Values<StorageReferenceBranch>>(2u, EmbeddedValuesDefinition(dataModel = { StorageReferenceBranch }))
    data object Numbers : StorageReferenceType<Map<String, Int>>(3u, MapDefinition(keyDefinition = StringDefinition(), valueDefinition = NumberDefinition(type = SInt32, unique = true)))
    data object Items : StorageReferenceType<List<Int>>(4u, ListDefinition(valueDefinition = NumberDefinition(type = SInt32, unique = true)))
    data object Members : StorageReferenceType<Set<Int>>(5u, SetDefinition(valueDefinition = NumberDefinition(type = SInt32, unique = true)))
    data object Scalar : StorageReferenceType<Int>(6u, NumberDefinition(type = SInt32))

    companion object : MultiTypeEnumDefinition<StorageReferenceType<out Any>>(
        StorageReferenceType::class, { listOf(Details, Branch, Numbers, Items, Members, Scalar) },
    )
}

private object StorageReferenceRecord : RootDataModel<StorageReferenceRecord>() {
    val children by map(4u, keyDefinition = StringDefinition(), valueDefinition = EmbeddedValuesDefinition(dataModel = { StorageReferenceDetails }))
    val entries by list(5u, valueDefinition = EmbeddedValuesDefinition(dataModel = { StorageReferenceDetails }))
    val typed by multiType(6u, typeEnum = StorageReferenceType)
    val branches by map(7u, keyDefinition = StringDefinition(), valueDefinition = EmbeddedValuesDefinition(dataModel = { StorageReferenceBranch }))
    val nested by map(8u, keyDefinition = StringDefinition(), valueDefinition = MapDefinition(keyDefinition = StringDefinition(), valueDefinition = NumberDefinition(type = SInt32, unique = true)))
    val lists by map(9u, keyDefinition = StringDefinition(), valueDefinition = ListDefinition(valueDefinition = NumberDefinition(type = SInt32, unique = true)))
    val sets by map(10u, keyDefinition = StringDefinition(), valueDefinition = SetDefinition(valueDefinition = NumberDefinition(type = SInt32, unique = true)))
    val typedEntries by list(11u, valueDefinition = MultiTypeDefinition(typeEnum = StorageReferenceType))
    val deep by map(12u, keyDefinition = StringDefinition(), valueDefinition = MapDefinition(keyDefinition = StringDefinition(), valueDefinition = EmbeddedValuesDefinition(dataModel = { StorageReferenceDetails })))
}

class StorageReferenceDescendantsTest {
    @Test
    fun mapChildQualifierResolvesItsUniqueLeafAndConsumesAllBytes() {
        val bytes = "2405666978656409".hexToByteArray()
        val decoded = decode(bytes)
        assertEquals("children.@fixed.double", decoded.completeName)
        assertEquals(Float64, assertIs<NumberDefinition<*>>(decoded.comparablePropertyDefinition).type)
        assertTrue((decoded.comparablePropertyDefinition as IsComparableDefinition<*, *>).unique)
        assertContentEquals(bytes, decoded.toStorageByteArray())
    }

    @Test
    fun listAndTypedEmbeddedLeavesConsumeTheirDescendants() {
        for ((fixture, name) in listOf(
            "2a0000000309" to "entries.@3.double",
            "310d09" to "typed.*Details.double",
        )) {
            val decoded = decode(fixture.hexToByteArray())
            assertEquals(name, decoded.completeName)
            assertTrue((decoded.comparablePropertyDefinition as IsComparableDefinition<*, *>).unique)
        }
    }

    @Test
    fun mixedEmbeddedContainerPathsResolveTheCompleteLeaf() {
        for (name in listOf(
            "branches.@outer.children.@inner.float",
            "branches.@outer.entries.@7.double",
            "typed.*Branch.children.@inner.double",
            "typed.*Branch.entries.@7.float",
        )) {
            val reference = StorageReferenceRecord.getPropertyReferenceByName(name)
            val decoded = decode(reference.toStorageByteArray())
            assertEquals(reference, decoded)
            assertTrue((decoded.comparablePropertyDefinition as IsComparableDefinition<*, *>).unique)
        }
    }

    @Test
    fun completeContainerReferencesRemainValidAndTrailingScalarBytesFail() {
        assertEquals("children.@fixed", decode("24056669786564".hexToByteArray()).completeName)
        assertFailsWith<DefNotFoundException> { decode("240566697865640909".hexToByteArray()) }
        assertFailsWith<ParseException> { decode("240566697865".hexToByteArray()) }
    }

    @Test
    fun collectionValuesInMapsAndTypedCasesResolveTheirUniqueItems() {
        for ((fixture, name) in listOf(
            "44056f757465720405696e6e6572" to "nested.@outer.@inner",
            "4c056f757465720200000002" to "lists.@outer.@2",
            "54056f75746572030480000007" to "sets.@outer.#7",
            "311d0405696e6e6572" to "typed.*Numbers.@inner",
            "31250200000002" to "typed.*Items.@2",
            "312d030480000007" to "typed.*Members.#7",
        )) {
            val bytes = fixture.hexToByteArray()
            val decoded = decode(bytes)
            assertEquals(name, decoded.completeName)
            assertTrue((decoded.comparablePropertyDefinition as IsComparableDefinition<*, *>).unique)
            assertContentEquals(bytes, decoded.toStorageByteArray())
        }
    }

    @Test
    fun referenceQualifiersMatchRealStorageRowsIncludingEmptyCollectionMarkers() {
        val values = StorageReferenceRecord.create {
            nested with mapOf("outer" to mapOf("inner" to 7), "empty" to emptyMap())
            lists with mapOf("outer" to listOf(6, 7), "empty" to emptyList())
            sets with mapOf("outer" to setOf(7), "empty" to emptySet())
            typedEntries with listOf(StorageReferenceType.Numbers(mapOf("inner" to 7)),
                StorageReferenceType.Items(listOf(6, 7)), StorageReferenceType.Members(setOf(7)))
            deep with mapOf("four" to mapOf("four" to StorageReferenceDetails.create { double with 7.0; float with 7.0f }))
        }
        val rows = verifyStorageRows(values)
        for (fixture in listOf("44056f757465720405696e6e6572", "4405656d70747904",
            "4c05656d70747902", "5405656d70747903", "5a000000001d0405696e6e6572",
            "6404666f75720404666f757209")) {
            assertTrue(rows.any { it.contentEquals(fixture.hexToByteArray()) }, fixture)
        }
        for (typed in listOf<TypedValue<StorageReferenceType<out Any>, Any>>(StorageReferenceType.Numbers(emptyMap()), StorageReferenceType.Items(emptyList()),
            StorageReferenceType.Members(emptySet()), StorageReferenceType.Scalar(7))) {
            verifyStorageRows(StorageReferenceRecord.create { this.typed with typed })
        }
    }

    @Test
    fun releasedReferenceBytesRetainTheirMissingAnonymousHeaders() {
        val current = "311d0405696e6e6572".hexToByteArray()
        assertContentEquals("311d05696e6e6572".hexToByteArray(), decode(current).toReleasedStorageByteArray())
        val fromList = "5a000000001d0405696e6e6572".hexToByteArray()
        assertContentEquals("5a000000001d05696e6e6572".hexToByteArray(), decode(fromList).toReleasedStorageByteArray())
        assertFailsWith<ParseException> { decode("44056f7574657205696e6e6572".hexToByteArray()) }
        assertFailsWith<ParseException> { decode("44056f75746572".hexToByteArray()) }
        assertFailsWith<ParseException> { decode("311d".hexToByteArray()) }
    }

    private fun verifyStorageRows(values: Values<StorageReferenceRecord>): List<ByteArray> = buildList {
        values.writeToStorage { _, qualifier, _, _ ->
            assertContentEquals(qualifier, decode(qualifier).toStorageByteArray())
            add(qualifier)
        }
    }

    private fun decode(bytes: ByteArray): AnyPropertyReference {
        var offset = 0
        val decoded = StorageReferenceRecord.getPropertyReferenceByStorageBytes(bytes.size, { bytes[offset++] })
        assertEquals(bytes.size, offset)
        return decoded
    }
}
