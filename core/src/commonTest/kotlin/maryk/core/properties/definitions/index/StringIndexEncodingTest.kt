package maryk.core.properties.definitions.index

import maryk.core.models.RootDataModel
import maryk.core.properties.definitions.string
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertFailsWith

private object StringIndexEncodingModel : RootDataModel<StringIndexEncodingModel>() {
    val value by string(1u)
}

class StringIndexEncodingTest {
    @Test
    fun directAndEncodedStringsKeepFormatTwoBytes() {
        val reference = StringIndexEncodingModel.value.ref()
        val reversed = Reversed(reference)
        val fixtures = mapOf(
            "" to "0000",
            "A" to "00ff410000",
            "\u0000" to "00ff00ff0000",
            "\u0100" to "0100ff0000",
            "😃" to "d83dde030000",
        )
        for ((text, hex) in fixtures) {
            val expected = hex.hexToByteArray()
            assertContentEquals(expected, reference.encodeIndexValue(text))
            assertContentEquals(expected, reference.encodeIndexComponent(text.encodeToByteArray()))
            assertContentEquals(expected.copyOfRange(0, expected.size - 2), reference.encodeIndexComponent(text.encodeToByteArray(), partial = true))
            assertContentEquals(expected.map { it.toInt().inv().toByte() }.toByteArray(), reversed.encodeIndexValue(text))
            assertContentEquals(reversed.encodeIndexValue(text), reversed.encodeIndexComponent(text.encodeToByteArray().map { it.toInt().inv().toByte() }.toByteArray()))
        }
    }

    @Test
    fun directStringsKeepMalformedSurrogateValidation() {
        assertFailsWith<IllegalArgumentException> { StringIndexEncodingModel.value.ref().encodeIndexValue("\uD800") }
        assertFailsWith<IllegalArgumentException> { StringIndexEncodingModel.value.ref().encodeIndexValue("\uDC00") }
    }
}
