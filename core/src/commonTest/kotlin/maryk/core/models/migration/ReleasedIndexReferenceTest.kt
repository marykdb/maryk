package maryk.core.models.migration

import maryk.core.models.DataModel
import maryk.core.models.RootDataModel
import maryk.core.models.ValueDataModel
import maryk.core.properties.definitions.EmbeddedValuesDefinition
import maryk.core.properties.definitions.NumberDefinition
import maryk.core.properties.definitions.IsMapDefinition
import maryk.core.properties.definitions.MapDefinition
import maryk.core.properties.definitions.StringDefinition
import maryk.core.properties.definitions.ValueObjectDefinition
import maryk.core.properties.definitions.index.AnyOf
import maryk.core.properties.definitions.index.IsIndexable
import maryk.core.properties.definitions.index.LegacyNormalize
import maryk.core.properties.definitions.index.Multiple
import maryk.core.properties.definitions.index.Normalize
import maryk.core.properties.definitions.index.ReferenceToMax
import maryk.core.properties.definitions.index.Reversed
import maryk.core.properties.definitions.index.Split
import maryk.core.properties.definitions.index.SplitOn
import maryk.core.properties.definitions.map
import maryk.core.properties.definitions.number
import maryk.core.properties.definitions.set
import maryk.core.properties.definitions.string
import maryk.core.properties.references.HasEmbeddedPropertyReference
import maryk.core.properties.references.IsIndexablePropertyReference
import maryk.core.properties.references.IsValuePropertyReference
import maryk.core.properties.IsPropertyContext
import maryk.core.properties.types.ValueDataObject
import maryk.core.properties.types.numeric.Float64
import maryk.core.properties.types.numeric.SInt32
import maryk.core.values.ObjectValues
import maryk.core.values.Values
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

private object IndexReferenceLeaf : DataModel<IndexReferenceLeaf>() {
    val n by number(1u, type = SInt32)
    val text by string(2u)
    val tags by set(3u, valueDefinition = StringDefinition())
    val labels by map(4u, keyDefinition = StringDefinition(), valueDefinition = StringDefinition())
}

private object FloatAncestorIndexModel : RootDataModel<FloatAncestorIndexModel>() {
    val items by map(
        1u,
        keyDefinition = NumberDefinition(type = Float64),
        valueDefinition = EmbeddedValuesDefinition(dataModel = { IndexReferenceLeaf }),
    )
}

private class IndexReferencePackedFloat(val number: Double) : ValueDataObject(toBytes(number)) {
    companion object : ValueDataModel<IndexReferencePackedFloat, Companion>(IndexReferencePackedFloat::class) {
        val number by number(1u, IndexReferencePackedFloat::number, type = Float64)
        override fun invoke(values: ObjectValues<IndexReferencePackedFloat, Companion>) = IndexReferencePackedFloat(values(1u))
    }
}

private object ValueObjectAncestorIndexModel : RootDataModel<ValueObjectAncestorIndexModel>() {
    val items by map(
        1u,
        keyDefinition = ValueObjectDefinition(dataModel = IndexReferencePackedFloat),
        valueDefinition = EmbeddedValuesDefinition(dataModel = { IndexReferenceLeaf }),
    )
}

private object NestedCollectionAncestorIndexModel : RootDataModel<NestedCollectionAncestorIndexModel>() {
    val items by map(
        1u,
        keyDefinition = StringDefinition(),
        valueDefinition = MapDefinition(keyDefinition = StringDefinition(), valueDefinition = EmbeddedValuesDefinition(dataModel = { IndexReferenceLeaf })),
    )
}

class ReleasedIndexReferenceTest {
    @Test
    fun integerIndexUnderFixedFloatingKeyRequiresDescriptorConversion() {
        val index = FloatAncestorIndexModel.ref { items at -1.0 }.getEmbedded("n", null) as IsIndexable
        assertContentEquals("5a0c08400fffffffffffff09".hexToByteArray(), index.referenceStorageByteArray.bytes)
        assertContentEquals("5a0c08c01000000000000009".hexToByteArray(), index.toReleasedReferenceStorageByteArray())
        assertTrue(index.requiresStorageFormatUpgrade())
        assertFalse(index.requiresKeyStorageFormatUpgrade())
        assertFalse(IndexReferenceLeaf.ref { n }.requiresStorageFormatUpgrade())
    }

    @Test
    fun integerIndexUnderFixedValueObjectKeyUsesReleasedPackedBytes() {
        val index = ValueObjectAncestorIndexModel.ref { items at IndexReferencePackedFloat(-1.0) }
            .getEmbedded("n", null) as IsIndexable
        assertContentEquals("5a0c08c01000000000000009".hexToByteArray(), index.toReleasedReferenceStorageByteArray())
        assertTrue(index.requiresStorageFormatUpgrade())
    }

    @Test
    @Suppress("UNCHECKED_CAST")
    fun nestedCollectionDescriptorPreservesTheReleasedMissingHeader() {
        val outer = NestedCollectionAncestorIndexModel.ref { items at "four" }
        val definition = outer.propertyDefinition as IsMapDefinition<String, Values<IndexReferenceLeaf>, IsPropertyContext>
        val index = definition.valueRef("fixed", outer).getEmbedded("n", null) as IsIndexable
        assertContentEquals("720c04666f75720405666978656409".hexToByteArray(), index.referenceStorageByteArray.bytes)
        assertContentEquals("6a0c04666f757205666978656409".hexToByteArray(), index.toReleasedReferenceStorageByteArray())
        assertTrue(index.requiresStorageFormatUpgrade())
    }

    @Test
    @Suppress("UNCHECKED_CAST")
    fun wrappersAndCompositeDescriptorsRetainTheirFraming() {
        val parent = FloatAncestorIndexModel.ref { items at -1.0 }
        val number = parent.getEmbedded("n", null) as IsValuePropertyReference<Int, *, *, *>
        val text = parent.getEmbedded("text", null) as IsIndexablePropertyReference<String>
        val indexes = listOf(
            Reversed(number),
            ReferenceToMax(number),
            Multiple(number, Reversed(number)),
            AnyOf(text),
            Normalize(text),
            LegacyNormalize(text),
            Split(Normalize(text), SplitOn.WordBoundary),
            Multiple(Normalize(text), AnyOf(text)),
        )
        for (index in indexes) {
            assertContentEquals(releasedFixture(index), index.toReleasedReferenceStorageByteArray())
            assertTrue(index.requiresStorageFormatUpgrade())
        }
    }

    @Test
    fun fuzzyLeafDescriptorsOnlyRewriteTheirFixedAncestors() {
        val parent = FloatAncestorIndexModel.ref { items at -1.0 }
        val tags = parent.getEmbedded("tags", null) as HasEmbeddedPropertyReference<*>
        val labels = parent.getEmbedded("labels", null) as HasEmbeddedPropertyReference<*>
        for (index in listOf(tags.getEmbedded("*", null), labels.getEmbedded("~", null))) {
            index as IsIndexable
            assertContentEquals(releasedFixture(index), index.toReleasedReferenceStorageByteArray())
        }
    }

    private fun releasedFixture(index: IsIndexable): ByteArray {
        // Every wrapper length is unchanged: replace the independently known fixed key payloads.
        val currentKey = "400fffffffffffff".hexToByteArray()
        val releasedKey = "c010000000000000".hexToByteArray()
        val bytes = index.referenceStorageByteArray.bytes.copyOf()
        var replacements = 0
        var offset = 0
        while (offset <= bytes.size - currentKey.size) {
            if (currentKey.indices.all { bytes[offset + it] == currentKey[it] }) {
                releasedKey.copyInto(bytes, offset)
                replacements++
                offset += currentKey.size
            } else offset++
        }
        assertTrue(replacements > 0)
        return bytes
    }
}
