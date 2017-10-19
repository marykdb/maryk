package maryk.core.properties.definitions

import io.kotlintest.matchers.shouldBe
import io.kotlintest.matchers.shouldThrow
import maryk.core.exceptions.DefNotFoundException
import maryk.core.properties.GrowableByteCollector
import maryk.core.properties.exceptions.PropertyInvalidValueException
import maryk.core.properties.exceptions.PropertyOutOfRangeException
import maryk.core.properties.types.TypedValue
import maryk.core.properties.types.numeric.SInt32
import maryk.core.protobuf.ProtoBuf
import maryk.core.protobuf.WireType
import org.junit.Test

internal class MultiTypeDefinitionTest {
    val intDef = NumberDefinition<Int>(
            name = "int",
            type = SInt32,
            maxValue = 1000
    )

    val stringDef = StringDefinition(
            name = "string",
            regEx = "#.*"
    )

    val def = MultiTypeDefinition(
            name = "multitype",
            index = 1,
            typeMap = mapOf(
                    0 to stringDef,
                    1 to intDef
            )
    )

    val multisToTest = arrayOf(
            TypedValue(0, "#test"),
            TypedValue(1, 400)
    )

    @Test
    fun testGet() {
        def.typeMap[0] shouldBe stringDef
        def.typeMap[1] shouldBe intDef
    }

    @Test
    fun testValidation() {
        def.validate(newValue = TypedValue(0, "#test"))
        def.validate(newValue = TypedValue(1, 400))

        shouldThrow<PropertyOutOfRangeException> {
            def.validate(newValue = TypedValue(1, 3000))
        }
        shouldThrow<PropertyInvalidValueException> {
            def.validate(newValue = TypedValue(0, "WRONG"))
        }
    }

    @Test
    fun testValidationInvalidField() {
        shouldThrow<DefNotFoundException> {
            def.validate(newValue = TypedValue(2, "NonExistingField"))
        }
    }

    @Test
    fun testTransportConversion() {
        val bc = GrowableByteCollector()
        multisToTest.forEach {
            def.writeTransportBytesWithKey(it, bc::reserve, bc::write)
            val key = ProtoBuf.readKey(bc::read)
            key.tag shouldBe 1
            key.wireType shouldBe WireType.START_GROUP
            def.readTransportBytes(
                    -1,
                    bc::read
            ) shouldBe it
            bc.reset()
        }
    }
}