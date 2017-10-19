package maryk.core.properties.definitions

import io.kotlintest.matchers.shouldBe
import io.kotlintest.matchers.shouldThrow
import maryk.TestMarykObject
import maryk.core.extensions.toHex
import maryk.core.properties.GrowableByteCollector
import maryk.core.properties.exceptions.PropertyInvalidValueException
import maryk.core.properties.exceptions.PropertyOutOfRangeException
import maryk.core.properties.exceptions.PropertyTooLittleItemsException
import maryk.core.properties.exceptions.PropertyTooMuchItemsException
import maryk.core.properties.exceptions.PropertyValidationUmbrellaException
import maryk.core.properties.types.numeric.SInt32
import maryk.core.protobuf.ProtoBuf
import maryk.core.protobuf.WireType
import org.junit.Test
import kotlin.test.assertTrue

internal class MapDefinitionTest {
    val intDef = NumberDefinition(
            type = SInt32,
            required = true,
            maxValue = 1000
    )

    val stringDef = StringDefinition(
            required = true,
            regEx = "#.*"
    )

    val subModelDef = SubModelDefinition(
            name = "marykRef",
            dataModel = TestMarykObject,
            required = true
    )

    val def = MapDefinition(
            name = "intStringMap",
            index = 4,
            minSize = 2,
            maxSize = 4,
            keyDefinition = intDef,
            valueDefinition = stringDef
    )

    val defSubModel = MapDefinition(
            name = "intSubmodelMap",
            keyDefinition = intDef,
            valueDefinition = subModelDef
    )

    @Test
    fun testReference() {
        def.getRef().dataModel shouldBe null
        defSubModel.getRef().dataModel shouldBe TestMarykObject
    }

    @Test
    fun testValidateSize() {
        def.validate(newValue = mapOf(
                12 to "#twelve",
                30 to "#thirty"
        ))
        def.validate(newValue = mapOf(
                12 to "#twelve",
                30 to "#thirty",
                100 to "#hundred",
                1000 to "#thousand"
        ))

        shouldThrow<PropertyTooLittleItemsException> {
            def.validate(newValue = mapOf(
                    1 to "#one"
            ))
        }

        shouldThrow<PropertyTooMuchItemsException> {
            def.validate(newValue = mapOf(
                    12 to "#twelve",
                    30 to "#thirty",
                    100 to "#hundred",
                    1000 to "#thousand",
                    0 to "#zero"
            ))
        }
    }

    @Test
    fun testValidateContent() {
        val e = shouldThrow<PropertyValidationUmbrellaException> {
            def.validate(newValue = mapOf(
                    12 to "#twelve",
                    30 to "WRONG",
                    1001 to "#thousandone",
                    3000 to "#threethousand"
            ))
        }
        e.exceptions.size shouldBe 3

        with(e.exceptions[0]) {
            assertTrue(this is PropertyInvalidValueException)
            this.reference!!.completeName shouldBe "intStringMap[30]"
        }

        with(e.exceptions[1]) {
            assertTrue(this is PropertyOutOfRangeException)
            this.reference!!.completeName shouldBe "intStringMap<1001>"
        }

        with(e.exceptions[2]) {
            assertTrue(this is PropertyOutOfRangeException)
            this.reference!!.completeName shouldBe "intStringMap<3000>"
        }
    }


    @Test
    fun testTransportConversion() {
        val bc = GrowableByteCollector()

        val value = mapOf(
                12 to "#twelve",
                30 to "#thirty",
                100 to "#hundred",
                1000 to "#thousand"
        )
        val asHex = "2308181207237477656c76652423083c120723746869727479242308c80112082368756e64726564242308d00f12092374686f7573616e6424"

        def.writeTransportBytesWithKey(value, bc::reserve, bc::write)

        bc.bytes.toHex() shouldBe asHex

        fun readKey() {
            val key = ProtoBuf.readKey(bc::read)
            key.wireType shouldBe WireType.START_GROUP
            key.tag shouldBe 4
        }

        fun readValue() = def.readMapTransportBytes(
                -1,
                bc::read
        )

        value.forEach {
            readKey()
            val mapValue = readValue()
            mapValue.first shouldBe it.key
            mapValue.second shouldBe it.value
        }
    }
}