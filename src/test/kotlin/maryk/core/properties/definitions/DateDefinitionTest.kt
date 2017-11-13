package maryk.core.properties.definitions

import maryk.core.properties.ByteCollector
import maryk.core.properties.exceptions.ParseException
import maryk.core.properties.types.Date
import maryk.test.shouldBe
import maryk.test.shouldThrow
import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.fail

internal class DateDefinitionTest {
    private val datesToTest = arrayOf(
            Date.nowUTC(),
            Date.MAX,
            Date.MIN
    )

    val def = DateDefinition(
            name = "dateTime"
    )

    @Test
    fun createNow() {
        def.createNow().epochDay shouldBe LocalDate.now().toEpochDay()
    }

    @Test
    fun testStorageBytesConversion() {
        val bc = ByteCollector()
        datesToTest.forEach {
            bc.reserve(
                    def.calculateStorageByteLength(it)
            )
            def.writeStorageBytes(it, bc::write)
            def.readStorageBytes(bc.size, bc::read) shouldBe it
            bc.reset()
        }
    }

    @Test
    fun testTransportBytesConversion() {
        val bc = ByteCollector()
        datesToTest.forEach {
            bc.reserve(
                def.calculateTransportByteLength(it, { fail("Should not call") })
            )
            def.writeTransportBytes(it, { fail("Should not call") }, bc::write)
            def.readTransportBytes(bc.size, bc::read) shouldBe it
            bc.reset()
        }
    }

    @Test
    fun convertString() {
        datesToTest.forEach {
            val b = def.asString(it)
            def.fromString(b) shouldBe it
        }
    }

    @Test
    fun convertWrongString() {
        shouldThrow<ParseException> {
            def.fromString("wrong")
        }
    }
}