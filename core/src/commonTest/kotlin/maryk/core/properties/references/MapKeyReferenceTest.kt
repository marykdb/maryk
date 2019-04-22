package maryk.core.properties.references

import maryk.core.exceptions.UnexpectedValueException
import maryk.core.processors.datastore.matchers.QualifierExactMatcher
import maryk.core.protobuf.WriteCache
import maryk.lib.extensions.toHex
import maryk.lib.time.Time
import maryk.test.ByteCollector
import maryk.test.models.TestMarykModel
import maryk.test.shouldBe
import maryk.test.shouldBeOfType
import maryk.test.shouldThrow
import kotlin.test.Test

class MapKeyReferenceTest {
    private val keyReference = TestMarykModel { map refToKey Time(12, 0, 1) }
    private val subKeyReference = TestMarykModel { embeddedValues { marykModel { map refToKey Time(15, 22, 55) } } }

    @Test
    fun getValueFromMap() {
        val map = mapOf(
            Time(12, 0, 1) to "right",
            Time(15, 22, 55) to "right2",
            Time(0, 0, 1) to "wrong",
            Time(2, 14, 52) to "wrong again"
        )

        this.keyReference.resolveFromAny(map) shouldBe Time(12, 0, 1)

        shouldThrow<UnexpectedValueException> {
            this.keyReference.resolveFromAny("wrongInput")
        }
    }

    @Test
    fun convertToProtoBufAndBack() {
        val bc = ByteCollector()
        val cache = WriteCache()

        bc.reserve(
            keyReference.calculateTransportByteLength(cache)
        )
        keyReference.writeTransportBytes(cache, bc::write)

        val converted = TestMarykModel.getPropertyReferenceByBytes(bc.size, bc::read)
        converted shouldBe keyReference
        bc.reset()
    }

    @Test
    fun testStringConversion() {
        keyReference.completeName shouldBe "map.\$12:00:01"

        TestMarykModel.getPropertyReferenceByName(keyReference.completeName) shouldBe keyReference
    }

    @Test
    fun writeAndReadKeyRefStorageBytes() {
        val bc = ByteCollector()

        bc.reserve(
            keyReference.calculateStorageByteLength()
        )
        keyReference.writeStorageBytes(bc::write)

        bc.bytes!!.toHex() shouldBe "080a0300a8c1"

        TestMarykModel.Properties.getPropertyReferenceByStorageBytes(bc.size, bc::read) shouldBe keyReference
    }

    @Test
    fun createKeyRefQualifierMatcher() {
        val matcher = keyReference.toQualifierMatcher()

        shouldBeOfType<QualifierExactMatcher>(matcher).qualifier.toHex() shouldBe "080a0300a8c1"
    }

    @Test
    fun writeAndReadDeepKeyRefStorageBytes() {
        val bc = ByteCollector()

        bc.reserve(
            subKeyReference.calculateStorageByteLength()
        )
        subKeyReference.writeStorageBytes(bc::write)

        bc.bytes!!.toHex() shouldBe "661e080a0300d84f"

        TestMarykModel.Properties.getPropertyReferenceByStorageBytes(bc.size, bc::read) shouldBe subKeyReference
    }
}
