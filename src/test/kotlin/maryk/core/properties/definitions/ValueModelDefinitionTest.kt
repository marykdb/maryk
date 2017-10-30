package maryk.core.properties.definitions

import io.kotlintest.matchers.shouldBe
import io.kotlintest.matchers.shouldThrow
import maryk.TestValueObject
import maryk.core.properties.ByteCollector
import maryk.core.properties.ByteCollectorWithLengthCacher
import maryk.core.properties.exceptions.PropertyOutOfRangeException
import maryk.core.properties.exceptions.PropertyValidationUmbrellaException
import maryk.core.properties.types.Date
import maryk.core.properties.types.DateTime
import maryk.core.properties.types.Time
import maryk.core.protobuf.ProtoBuf
import maryk.core.protobuf.WireType
import org.junit.Test

internal class ValueModelDefinitionTest {
    val def = ValueModelDefinition(
            name = "test",
            dataModel = TestValueObject
    )

    @Test
    fun hasValues() {
        def.dataModel shouldBe TestValueObject
    }

    val value = TestValueObject(
            int = 4,
            dateTime = DateTime(date = Date.nowUTC(), time = Time.nowUTC().copy(milli = 0)),
            bool = true
    )

    @Test
    fun testConvertStorageBytes() {
        val bc = ByteCollector()
        bc.reserve(
                def.calculateStorageByteLength(value)
        )
        def.writeStorageBytes(value, bc::write)
        val new = def.readStorageBytes(null, bc.size, bc::read)

        new shouldBe value
    }

    @Test
    fun testTransportConversion() {
        val bc = ByteCollectorWithLengthCacher()

        bc.reserve(def.calculateTransportByteLengthWithKey(value, bc::addToCache))
        def.writeTransportBytesWithKey(value, bc::nextLengthFromCache, bc::write)
        bc.bytes!!.size shouldBe 20

        val key = ProtoBuf.readKey(bc::read)
        key.wireType shouldBe WireType.LENGTH_DELIMITED
        key.tag shouldBe -1

        def.readTransportBytes(
                null,
                ProtoBuf.getLength(key.wireType, bc::read),
                bc::read
        ) shouldBe value
    }

    @Test
    fun testConvertString() {
        def.fromString(
                def.asString(value)
        ) shouldBe value
    }

    @Test
    fun validate() {
        def.validate(newValue = TestValueObject(
                int = 4,
                dateTime = DateTime.nowUTC(),
                bool = true
        ))
        val e = shouldThrow<PropertyValidationUmbrellaException> {
            def.validate(newValue = TestValueObject(
                    int = 1000,
                    dateTime = DateTime.nowUTC(),
                    bool = true
            ))
        }

        e.exceptions.size shouldBe 1

        with (e.exceptions[0]) {
            this is PropertyOutOfRangeException
            this.reference!!.completeName shouldBe "test.int"
        }
    }
}