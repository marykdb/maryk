package maryk.core.properties.definitions.index

import maryk.checkJsonConversion
import maryk.checkProtoBufConversion
import maryk.checkYamlConversion
import maryk.core.models.RootDataModel
import maryk.core.models.IsRootDataModel
import maryk.core.properties.definitions.StringDefinition
import maryk.core.properties.definitions.IsPropertyDefinition
import maryk.core.properties.definitions.set
import maryk.core.properties.definitions.string
import maryk.core.properties.exceptions.InvalidValueException
import maryk.core.properties.references.AnyPropertyReference
import maryk.core.properties.references.IsIndexablePropertyReference
import maryk.core.properties.references.IsPropertyReference
import maryk.core.properties.types.Bytes
import maryk.core.query.DefinitionsConversionContext
import maryk.core.values.IsValuesGetter
import maryk.core.values.IsStreamingValuesGetter
import maryk.test.models.TestMarykModel
import kotlinx.datetime.LocalTime
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.expect

class MultipleTest {
    private class StreamingCollectionsValuesGetter(
        private val mapReference: AnyPropertyReference,
        private val mapKeys: List<Any>,
        private val setReference: AnyPropertyReference,
        private val setValues: List<Any>,
    ) : IsStreamingValuesGetter {
        var collectionGets = 0
        var maximumActiveValues = 0
        var mapEnumerations = 0
        var setEnumerations = 0
        private var activeValues = 0

        override fun <T : Any, D : IsPropertyDefinition<T>, C : Any> get(
            propertyReference: IsPropertyReference<T, D, C>
        ): T? {
            collectionGets++
            error("Collection values must be streamed instead of read")
        }

        override fun streamMapKeys(parent: AnyPropertyReference, emit: (Any) -> Unit) =
            stream(parent, mapReference, mapKeys, emit) { mapEnumerations++ }

        override fun streamSetValues(parent: AnyPropertyReference, emit: (Any) -> Unit) =
            stream(parent, setReference, setValues, emit) { setEnumerations++ }

        private fun stream(
            parent: AnyPropertyReference,
            expectedParent: AnyPropertyReference,
            values: List<Any>,
            emit: (Any) -> Unit,
            onEnumerate: () -> Unit,
        ): Boolean {
            if (parent !== expectedParent) return false
            onEnumerate()
            values.forEach { value ->
                activeValues++
                maximumActiveValues = maxOf(maximumActiveValues, activeValues)
                emit(value)
                activeValues--
            }
            return true
        }
    }

    private class LegacyFanoutIndexable : IsIndexable {
        override val indexKeyPartType: IndexKeyPartType<IsIndexable> = IndexKeyPartType.UUIDv4
        override val referenceStorageByteArray = Bytes(ByteArray(0))

        override fun toStorageByteArrays(values: IsValuesGetter) = listOf(
            byteArrayOf(0x01),
            byteArrayOf(0x02),
        )

        override fun toStorageByteArraysForIndex(values: IsValuesGetter, key: ByteArray?) = listOf(
            byteArrayOf(0x31),
            byteArrayOf(0x32),
        )

        override fun calculateReferenceStorageByteLength() = 0
        override fun writeReferenceStorageBytes(writer: (Byte) -> Unit) = Unit
        override fun calculateStorageByteLengthForIndex(values: IsValuesGetter, keySize: Int?) = 0
        override fun writeStorageBytesForIndex(values: IsValuesGetter, key: ByteArray?, writer: (Byte) -> Unit) = Unit
        override fun writeStorageBytes(values: IsValuesGetter, writer: (Byte) -> Unit) = writer(0x7f)
        override fun isCompatibleWithModel(dataModel: IsRootDataModel) = true
    }

    private class DefaultIndexable : IsIndexable {
        override val indexKeyPartType: IndexKeyPartType<IsIndexable> = IndexKeyPartType.UUIDv4
        override val referenceStorageByteArray = Bytes(ByteArray(0))

        override fun calculateReferenceStorageByteLength() = 0
        override fun writeReferenceStorageBytes(writer: (Byte) -> Unit) = Unit
        override fun calculateStorageByteLengthForIndex(values: IsValuesGetter, keySize: Int?) = 0
        override fun writeStorageBytesForIndex(values: IsValuesGetter, key: ByteArray?, writer: (Byte) -> Unit) = Unit
        override fun writeStorageBytes(values: IsValuesGetter, writer: (Byte) -> Unit) = writer(0x7f)
        override fun isCompatibleWithModel(dataModel: IsRootDataModel) = true
    }

    private class LegacyStringFanoutReference(
        private val sharedBytes: ByteArray
    ) : IsIndexablePropertyReference<String> {
        override val indexKeyPartType: IndexKeyPartType<IsIndexable> = IndexKeyPartType.Reference
        override val referenceStorageByteArray = Bytes(ByteArray(0))

        override fun getValue(values: IsValuesGetter) = ""
        override fun toStorageByteArrays(values: IsValuesGetter) = listOf(sharedBytes, sharedBytes)
        override fun calculateStorageByteLength(value: String) = value.length
        override fun writeStorageBytes(value: String, writer: (Byte) -> Unit) = value.encodeToByteArray().forEach(writer)
        override fun readStorageBytes(length: Int, reader: () -> Byte) = ByteArray(length) { reader() }.decodeToString()
        override fun isForPropertyReference(propertyReference: AnyPropertyReference) = false
        override fun toQualifierStorageByteArray() = null
        override fun calculateReferenceStorageByteLength() = 0
        override fun writeReferenceStorageBytes(writer: (Byte) -> Unit) = Unit
        override fun isCompatibleWithModel(dataModel: IsRootDataModel) = true
    }

    private fun assertStreamedEntriesMatchLegacy(
        index: IsIndexable,
        values: IsValuesGetter,
        key: ByteArray? = null,
    ) {
        val legacy = index.toStorageByteArraysForIndex(values, key)
        var emissionIndex = 0
        index.forEachStorageByteArrayForIndex(values, key) { streamed ->
            assertContentEquals(legacy[emissionIndex++], streamed)
        }

        assertEquals(legacy.size, emissionIndex)
    }

    object FanoutModel : RootDataModel<FanoutModel>() {
        val family by string(index = 1u, final = true)
        val given by set(
            index = 2u,
            required = false,
            final = true,
            valueDefinition = StringDefinition()
        )
    }

    object NestedAnyOfModel : RootDataModel<NestedAnyOfModel>() {
        val prefix by string(index = 1u, final = true)
        val first by string(index = 2u, required = false, final = true)
        val second by string(index = 3u, required = false, final = true)
    }

    private val multiple = TestMarykModel.run {
        Multiple(
            UUIDv4Key,
            Reversed(bool.ref()),
            multi.typeRef(),
            string.ref(),
            Reversed(string.ref()),
            int.ref()
        )
    }

    private val context = DefinitionsConversionContext(
        propertyDefinitions = TestMarykModel
    )

    @Test
    fun convertDefinitionToProtoBufAndBack() {
        checkProtoBufConversion(
            value = multiple,
            dataModel = Multiple.Model,
            context = { context }
        )
    }

    @Test
    fun convertDefinitionToJSONAndBack() {
        checkJsonConversion(
            value = multiple,
            dataModel = Multiple.Model,
            context = { context }
        )
    }

    @Test
    fun convertDefinitionToYAMLAndBack() {
        expect(
            """
            - !UUIDv4
            - !Reversed bool
            - !Ref multi.*
            - !Ref string
            - !Reversed string
            - !Ref int

            """.trimIndent()
        ) {
            checkYamlConversion(
                value = multiple,
                dataModel = Multiple.Model,
                context = { context }
            )
        }
    }

    @Test
    fun toReferenceStorageBytes() {
        expect("040101020b31020a69020a09020b09020a11") { multiple.toReferenceStorageByteArray().toHexString() }
    }

    @Test
    fun emptyMultipleIsRejected() {
        assertFailsWith<IllegalArgumentException> {
            Multiple()
        }
    }

    @Test
    fun checkedIndexByteLengthRejectsOverflow() {
        assertFailsWith<IllegalArgumentException> {
            Int.MAX_VALUE.checkedIndexByteLengthPlus(1)
        }
    }

    @Test
    fun checkedIndexByteLengthRejectsNegativeAddend() {
        assertFailsWith<IllegalArgumentException> {
            0.checkedIndexByteLengthPlus(-1)
        }
    }

    @Test
    fun calculateStorageByteLengthForIndexUsesLongestFanoutCombination() {
        val index = Multiple(
            FanoutModel.family.ref(),
            AnyOf(
                FanoutModel.family.ref(),
                FanoutModel { given.refToAny() }
            )
        )
        val values = FanoutModel.create {
            family with "abc"
            given with setOf("z", "longer")
        }

        val lengths = index.toStorageByteArraysForIndex(values).map { it.size }
        assertEquals(lengths.max(), index.calculateStorageByteLengthForIndex(values))
    }

    @Test
    fun nestedMultipleSelectsLaterAnyOfValue() {
        val nested = Multiple(
            AnyOf(NestedAnyOfModel.first.ref(), NestedAnyOfModel.second.ref())
        )
        val index = Multiple(NestedAnyOfModel.prefix.ref(), nested)
        val values = NestedAnyOfModel.create {
            prefix with "prefix"
            second with "second"
        }

        assertEquals(1, index.toStorageByteArraysForIndex(values).size)
    }

    @Test
    fun anyOfFanoutKeepsBothExactEntries() {
        val index = Multiple(
            NestedAnyOfModel.prefix.ref(),
            AnyOf(NestedAnyOfModel.first.ref(), NestedAnyOfModel.second.ref())
        )
        val values = NestedAnyOfModel.create {
            prefix with "prefix"
            first with "first"
            second with "second"
        }

        val entries = index.toStorageByteArraysForIndex(values)

        assertEquals(2, entries.size)
        assertEquals(
            listOf(
                "70726566697866697273740506",
                "7072656669787365636f6e640606",
            ),
            entries.map { it.toHexString() },
        )
    }

    @Test
    fun streamedIndexEntriesMatchCartesianFanoutOrderAndLayout() {
        val index = Multiple(
            FanoutModel.family.ref(),
            AnyOf(FanoutModel.family.ref(), FanoutModel { given.refToAny() }),
            FanoutModel { given.refToAny() },
        )
        val values = FanoutModel.create {
            family with "abc"
            given with setOf("z", "longer")
        }
        val key = byteArrayOf(0x0a, 0x0b)
        val streamed = mutableListOf<ByteArray>()

        index.forEachStorageByteArrayForIndex(values, key) { streamed += it }

        assertEquals(
            listOf(
                "6162636162637a0103030a0b",
                "6162636162636c6f6e6765720603030a0b",
                "6162637a7a0101030a0b",
                "6162637a6c6f6e6765720601030a0b",
                "6162636c6f6e6765727a0106030a0b",
                "6162636c6f6e6765726c6f6e6765720606030a0b",
            ),
            streamed.map { it.toHexString() },
        )
        assertStreamedEntriesMatchLegacy(index, values, key)
    }

    @Test
    fun streamedEntriesKeepAnyOfByteArrayIdentityDuplicates() {
        val index = AnyOf(FanoutModel.family.ref(), FanoutModel.family.ref())
        val values = FanoutModel.create {
            family with "abc"
        }
        val streamed = mutableListOf<ByteArray>()

        index.forEachStorageByteArrayForIndex(values) { streamed += it }

        assertEquals(
            listOf("61626303", "61626303"),
            streamed.map { it.toHexString() },
        )
        assertStreamedEntriesMatchLegacy(index, values)
    }

    @Test
    fun streamedMultipleEntriesStayEmptyWhenOneFanoutReferenceIsMissing() {
        val index = Multiple(FanoutModel.family.ref(), FanoutModel { given.refToAny() })
        val values = FanoutModel.create {
            family with "abc"
        }
        var count = 0

        index.forEachStorageByteArrayForIndex(values) { count++ }

        assertEquals(0, count)
        assertStreamedEntriesMatchLegacy(index, values)
    }

    @Test
    fun streamedMultipleEntriesSupportHighFanoutWithoutCollectingEntries() {
        val index = Multiple(
            FanoutModel { given.refToAny() },
            FanoutModel { given.refToAny() },
        )
        val values = FanoutModel.create {
            family with "unused"
            given with (0 until 100).map { "value-$it" }.toSet()
        }
        val legacy = index.toStorageByteArraysForIndex(values)
        var emissionIndex = 0

        index.forEachStorageByteArrayForIndex(values) { streamed ->
            assertContentEquals(legacy[emissionIndex++], streamed)
        }

        assertEquals(10_000, emissionIndex)
        assertEquals(legacy.size, emissionIndex)
    }

    @Test
    fun streamedMapAnyKeyEntriesFanOutOverEveryStoredKey() {
        val index = TestMarykModel { map.refToAnyKey() }
        val values = TestMarykModel.create(setDefaults = false) {
            map with linkedMapOf(
                LocalTime(9, 0) to "first",
                LocalTime(10, 0) to "second",
            )
        }
        var count = 0

        index.forEachStorageByteArrayForIndex(values) { count++ }

        assertEquals(2, count)
        assertStreamedEntriesMatchLegacy(index, values)
    }

    @Test
    fun streamedEntriesUseCustomLegacyFanoutOverrides() {
        val index = LegacyFanoutIndexable()
        val values = FanoutModel.create { family with "abc" }
        val raw = mutableListOf<ByteArray>()
        val entries = mutableListOf<ByteArray>()
        val compositeEntries = mutableListOf<ByteArray>()

        index.forEachStorageByteArray(values) { raw += it }
        index.forEachStorageByteArrayForIndex(values) { entries += it }
        Multiple(index, index).forEachStorageByteArrayForIndex(values) { compositeEntries += it }

        assertEquals(listOf("01", "02"), raw.map { it.toHexString() })
        assertEquals(listOf("31", "32"), entries.map { it.toHexString() })
        assertEquals(4, compositeEntries.size)
    }

    @Test
    fun streamedEntriesPropagateConsumerValidationFailures() {
        val values = FanoutModel.create { family with "abc" }

        assertFailsWith<InvalidValueException> {
            DefaultIndexable().forEachStorageByteArrayForIndex(values) {
                throw InvalidValueException(null, "stop")
            }
        }
    }

    @Test
    fun streamedAnyOfKeepsLegacyRepeatedByteArrayIdentityDeduplication() {
        val sharedBytes = byteArrayOf(0x41)
        val index = AnyOf(LegacyStringFanoutReference(sharedBytes))
        val values = FanoutModel.create { family with "abc" }
        val streamed = mutableListOf<ByteArray>()

        index.forEachStorageByteArray(values) { streamed += it }

        assertEquals(listOf("41"), index.toStorageByteArrays(values).map { it.toHexString() })
        assertEquals(listOf("41"), streamed.map { it.toHexString() })
    }

    @Test
    fun streamedMapAndSetReferencesUseGetterCallbacksWithoutCollectionReads() {
        val mapIndex = TestMarykModel { map.refToAnyKey() }
        val setIndex = FanoutModel { given.refToAny() }
        val index = Multiple(mapIndex, setIndex, setIndex)
        val values = StreamingCollectionsValuesGetter(
            mapReference = mapIndex.parentReference!!,
            mapKeys = listOf(LocalTime(9, 0), LocalTime(10, 0)),
            setReference = setIndex.parentReference!!,
            setValues = listOf("first", "second", "third"),
        )
        var entryCount = 0

        index.forEachStorageByteArrayForIndex(values) { entryCount++ }

        assertEquals(18, entryCount)
        assertEquals(0, values.collectionGets)
        assertEquals(3, values.maximumActiveValues)
        assertEquals(1, values.mapEnumerations)
        assertEquals(8, values.setEnumerations)
    }
}
