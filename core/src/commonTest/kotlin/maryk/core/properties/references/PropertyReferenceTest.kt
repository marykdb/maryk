package maryk.core.properties.references

import maryk.core.exceptions.UnexpectedValueException
import maryk.core.models.RootDataModel
import maryk.core.processors.datastore.matchers.QualifierExactMatcher
import maryk.core.properties.PropertyDefinitions
import maryk.core.properties.definitions.EmbeddedValuesDefinition
import maryk.core.properties.definitions.StringDefinition
import maryk.core.properties.references.Properties.definition
import maryk.core.properties.references.Properties.modelDefinition
import maryk.core.protobuf.WriteCache
import maryk.lib.extensions.toHex
import maryk.test.ByteCollector
import maryk.test.assertType
import kotlin.test.Test
import kotlin.test.assertFailsWith
import kotlin.test.assertNotEquals
import kotlin.test.assertSame
import kotlin.test.expect

private object Properties : PropertyDefinitions() {
    val definition = add(1u, "test", StringDefinition())
    val modelDefinition = add(2u, "embeddedObject", EmbeddedValuesDefinition(
        dataModel = { Model }
    ))
}

private object Model : RootDataModel<Model, Properties>(
    properties = Properties
)

private val ref = definition.ref()
private val subRef = definition.ref(modelDefinition.ref())

internal class PropertyReferenceTest {
    @Test
    fun cacheTest() {
        assertSame(ref, definition.ref())
        assertSame(subRef, definition.ref(modelDefinition.ref()))
    }

    @Test
    fun getValueFromList() {
        val values = Model.values {
            mapNonNulls(
                definition with "±testValue"
            )
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
        expect(definition.ref()) { ref }
        assertNotEquals<IsPropertyReference<*, *, *>>(
            modelDefinition.ref(), ref
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

        expect(subRef) { Properties.getPropertyReferenceByBytes(bc.size, bc::read) }
    }

    @Test
    fun writeAndReadStorageBytes() {
        val bc = ByteCollector()

        bc.reserve(
            subRef.calculateStorageByteLength()
        )
        subRef.writeStorageBytes(bc::write)

        expect("1609") { bc.bytes!!.toHex() }

        expect(subRef) { Properties.getPropertyReferenceByStorageBytes(bc.size, bc::read) }
    }

    @Test
    fun createMatcher() {
        val matcher = subRef.toQualifierMatcher()

        assertType<QualifierExactMatcher>(matcher).apply {
            expect("1609") { qualifier.toHex() }
        }
    }
}
