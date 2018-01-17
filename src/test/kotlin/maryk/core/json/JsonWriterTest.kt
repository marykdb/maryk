package maryk.core.json

import maryk.test.shouldBe
import kotlin.test.Test

internal class JsonWriterTest : AbstractJsonWriterTest() {
    override fun createJsonWriter(writer: (String) -> Unit) = JsonWriter(writer = writer)

    @Test
    fun write_expected_JSON() {
        var output = ""
        val writer = { string: String -> output += string }

        val generator = JsonWriter(writer = writer)

        writeJson(generator)

        output shouldBe "[1,\"#Test\",3.5,true,{\"test\":false,\"test2\":\"value\"},{\"another\":\"yes\"}]"
    }

    @Test
    fun write_expected_pretty_JSON() {
        var output = ""
        val writer = { string: String -> output += string }

        val generator = JsonWriter(pretty = true, writer = writer)

        writeJson(generator)

        output shouldBe "[1, \"#Test\", 3.5, true, {\n" +
                "\t\"test\": false,\n" +
                "\t\"test2\": \"value\"\n" +
                "}, {\n" +
                "\t\"another\": \"yes\"\n" +
                "}]"
    }
}