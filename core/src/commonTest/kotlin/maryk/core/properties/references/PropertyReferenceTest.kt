package maryk.core.properties.references

import maryk.core.exceptions.UnexpectedValueException
import maryk.core.models.RootDataModel
import maryk.core.processors.datastore.matchers.FuzzyExactLengthMatch
import maryk.core.processors.datastore.matchers.QualifierExactMatcher
import maryk.core.processors.datastore.matchers.QualifierFuzzyMatcher
import maryk.core.processors.datastore.matchers.ReferencedQualifierMatcher
import maryk.core.properties.IsPropertyContext
import maryk.core.properties.definitions.StringDefinition
import maryk.core.properties.definitions.embed
import maryk.core.properties.definitions.string
import maryk.core.properties.definitions.wrapper.FlexBytesDefinitionWrapper
import maryk.core.properties.references.Model.test
import maryk.core.properties.references.dsl.any
import maryk.core.protobuf.WriteCache
import maryk.lib.extensions.toHex
import maryk.test.ByteCollector
import maryk.test.models.ComplexModel
import maryk.test.models.TestMarykModel
import kotlin.test.Test
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotEquals
import kotlin.test.assertSame
import kotlin.test.assertTrue
import kotlin.test.expect

private object Model : RootDataModel<Model>() {
    val test by string(1u)
    val embeddedObject by embed(
        index = 2u,
        dataModel = { this@Model }
    )
}

private val ref = test.ref()
private val subRef = test.ref(Model.embeddedObject.ref())

internal class PropertyReferenceTest {
    @Test
    fun cacheTest() {
        assertSame(ref, test.ref())
        assertSame(subRef, test.ref(Model.embeddedObject.ref()))
    }

    @Test
    fun getValueFromList() {
        val values = Model.create {
            test with "±testValue"
        }

        expect("±testValue") { ref.resolveFromAny(values) }

        assertFailsWith<UnexpectedValueException> {
            ref.resolveFromAny(123)
        }
    }

    @Test
    fun unwrap() {
        val list = subRef.unwrap()

        expect("embeddedObject") { list[0].completeName }
        expect(subRef) { list[1] as ValueWithFlexBytesPropertyReference<*, *, *, *> }
    }

    @Test
    fun getCompleteName() {
        expect("test") { ref.completeName }
        expect("embeddedObject.test") { subRef.completeName }
    }

    @Test
    fun testHashCode() {
        expect("test".hashCode()) { ref.hashCode() }
    }

    @Test
    fun testCompareTo() {
        expect(test.ref()) { ref }
        assertNotEquals<IsPropertyReference<*, *, *>>(
            Model.embeddedObject.ref(), ref
        )
    }

    @Test
    fun writeAndReadTransportBytes() {
        val bc = ByteCollector()
        val cache = WriteCache()

        bc.reserve(
            subRef.calculateTransportByteLength(cache)
        )
        subRef.writeTransportBytes(cache, bc::write)

        expect("0201") { bc.bytes!!.toHex() }

        expect(subRef) { Model.getPropertyReferenceByBytes(bc.size, bc::read) }
    }

    @Test
    fun writeAndReadStorageBytes() {
        val bc = ByteCollector()

        bc.reserve(
            subRef.calculateStorageByteLength()
        )
        subRef.writeStorageBytes(bc::write)

        expect("1609") { bc.bytes!!.toHex() }

        expect(subRef) { Model.getPropertyReferenceByStorageBytes(bc.size, bc::read) }
    }

    @Test
    fun compatibleWithModel() {
        assertTrue {
            ref.isCompatibleWithModel(Model)
        }

        assertTrue {
            subRef.isCompatibleWithModel(Model)
        }

        // Property definition wrapper which does not exist on Model
        val invalid = FlexBytesDefinitionWrapper<String, String, IsPropertyContext, StringDefinition, Any>(
            3u,
            "invalid",
            StringDefinition()
        )

        val invalidRef = invalid.ref()
        assertFalse {
            invalidRef.isCompatibleWithModel(Model)
        }

        val invalidSubRef = invalid.ref(Model.embeddedObject.ref())
        assertFalse {
            invalidSubRef.isCompatibleWithModel(Model)
        }
    }

    @Test
    fun createMatcher() {
        val matcher = subRef.toQualifierMatcher()

        assertIs<QualifierExactMatcher>(matcher).apply {
            expect("1609") { qualifier.toHex() }
        }
    }

    @Test
    fun createReferenceMatcher() {
        val matcher = TestMarykModel { reference { string::ref } }.toQualifierMatcher()

        assertIs<QualifierExactMatcher>(matcher).apply {
            expect("71") { qualifier.toHex() }
            assertIs<ReferencedQualifierMatcher>(referencedQualifierMatcher).apply {
                expect(TestMarykModel { reference::ref }) { reference }
                assertIs<QualifierExactMatcher>(qualifierMatcher).apply {
                    expect("09") { qualifier.toHex() }
                }
            }
        }
    }

    @Test
    fun createFuzzyReferenceMatcher() {
        val matcher = ComplexModel { incMap.any { marykModel { reference { map.refToAny() } } } }.toQualifierMatcher()

        assertIs<QualifierFuzzyMatcher>(matcher).apply {
            expect("44") { firstPossible().toHex() }
            expect(2) { qualifierParts.size }
            expect("1e71") { qualifierParts[1].toHex() }
            expect(1) { fuzzyMatchers.size }

            fuzzyMatchers.first().let { matcher ->
                assertIs<FuzzyExactLengthMatch>(matcher).apply {
                    expect(4) { length }
                }
            }

            assertIs<ReferencedQualifierMatcher>(referencedQualifierMatcher).apply {
                expect(ComplexModel { incMap.any { marykModel { reference::ref } } }) { reference }

                assertIs<QualifierFuzzyMatcher>(qualifierMatcher).apply {
                    expect("54") { firstPossible().toHex() }
                    expect(1) { qualifierParts.size }
                    expect(1) { fuzzyMatchers.size }

                    fuzzyMatchers.first().let { matcher ->
                        assertIs<FuzzyExactLengthMatch>(matcher).apply {
                            expect(3) { length }
                        }
                    }
                }
            }
        }
    }
}
