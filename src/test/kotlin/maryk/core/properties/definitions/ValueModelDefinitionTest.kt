package maryk.core.properties.definitions

import maryk.TestValueObject
import maryk.checkProtoBufConversion
import maryk.core.properties.ByteCollector
import maryk.core.properties.ByteCollectorWithLengthCacher
import maryk.core.properties.exceptions.OutOfRangeException
import maryk.core.properties.exceptions.ValidationUmbrellaException
import maryk.core.properties.types.Date
import maryk.core.properties.types.DateTime
import maryk.core.properties.types.Time
import maryk.test.shouldBe
import maryk.test.shouldThrow
import kotlin.test.Test

internal class ValueModelDefinitionTest {
    private val def = ValueModelDefinition(
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
    fun `convert values to storage bytes and back`() {
        val bc = ByteCollector()
        bc.reserve(
                def.calculateStorageByteLength(value)
        )
        def.writeStorageBytes(value, bc::write)
        val new = def.readStorageBytes(bc.size, bc::read)

        new shouldBe value
    }

    @Test
    fun `convert values to transport bytes and back`() {
        val bc = ByteCollectorWithLengthCacher()

        checkProtoBufConversion(bc, value, this.def)
    }

    @Test
    fun `convert values to String and back`() {
        def.fromString(
                def.asString(value)
        ) shouldBe value
    }

    @Test
    fun validate() {
        def.validateWithRef(newValue = TestValueObject(
                int = 4,
                dateTime = DateTime.nowUTC(),
                bool = true
        ))
        val e = shouldThrow<ValidationUmbrellaException> {
            def.validateWithRef(newValue = TestValueObject(
                    int = 1000,
                    dateTime = DateTime.nowUTC(),
                    bool = true
            ))
        }

        e.exceptions.size shouldBe 1

        with (e.exceptions[0] as OutOfRangeException) {
            this.reference!!.completeName shouldBe "int"
        }
    }
}