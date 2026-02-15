package maryk.core.properties.references

import kotlinx.datetime.LocalTime
import maryk.core.exceptions.UnexpectedValueException
import maryk.core.processors.datastore.matchers.QualifierExactMatcher
import maryk.core.protobuf.WriteCache
import maryk.test.ByteCollector
import maryk.test.models.TestMarykModel
import kotlin.test.Test
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertSame
import kotlin.test.expect

class MapValueReferenceTest {
    private val valReference = TestMarykModel { map refAt LocalTime(15, 22, 55) }
    private val subReference = TestMarykModel { embeddedValues { marykModel { map refAt LocalTime(15, 22, 55) } } }

    @Test
    fun cacheReferenceTest() {
        assertSame(valReference, TestMarykModel { map refAt LocalTime(15, 22, 55) })
        assertSame(subReference, TestMarykModel { embeddedValues { marykModel { map refAt LocalTime(15, 22, 55) } } })
    }

    @Test
    fun getValueFromMap() {
        val map = mapOf(
            LocalTime(12, 0, 1) to "right",
            LocalTime(15, 22, 55) to "right2",
            LocalTime(0, 0, 1) to "wrong",
            LocalTime(2, 14, 52) to "wrong again"
        )

        expect("right2") { this.valReference.resolveFromAny(map) }

        assertFailsWith<UnexpectedValueException> {
            this.valReference.resolveFromAny("wrongInput")
        }
    }

    @Test
    fun convertToProtoBufAndBack() {
        ByteCollector().apply {
            val cache = WriteCache()

            reserve(
                valReference.calculateTransportByteLength(cache)
            )
            valReference.writeTransportBytes(cache, ::write)

            expect(valReference) { TestMarykModel.getPropertyReferenceByBytes(size, ::read) }
        }
    }

    @Test
    fun testStringConversion() {
        expect("map.@15:22:55") { valReference.completeName }
        expect(valReference) { TestMarykModel.getPropertyReferenceByName(valReference.completeName) }
    }

    @Test
    fun writeAndReadValueRefStorageBytes() {
        ByteCollector().apply {
            reserve(
                valReference.calculateStorageByteLength()
            )
            valReference.writeStorageBytes(::write)

            expect("540300d84f") { bytes!!.toHexString() }

            expect(valReference) { TestMarykModel.getPropertyReferenceByStorageBytes(size, ::read) }
        }

    }

    @Test
    fun writeAndReadDeepValueRefStorageBytes() {
        ByteCollector().apply {
            reserve(
                subReference.calculateStorageByteLength()
            )
            subReference.writeStorageBytes(::write)

            expect("661e540300d84f") { bytes!!.toHexString() }

            expect(subReference) { TestMarykModel.getPropertyReferenceByStorageBytes(size, ::read) }
        }
    }

    @Test
    fun createValueRefQualifierMatcher() {
        val matcher = subReference.toQualifierMatcher()

        expect("661e540300d84f") { assertIs<QualifierExactMatcher>(matcher).qualifier.toHexString() }
    }
}
