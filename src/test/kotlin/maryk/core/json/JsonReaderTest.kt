package maryk.core.json

import maryk.test.shouldBe
import maryk.test.shouldThrow
import kotlin.test.Test

internal class JsonReaderTest {
    @Test
    fun testJsonParserStructure() {
        val input = """{
            "string": "hey",
            "int": 4,
            "array": [34, 2352, 3423, true, false, null],
            "emptyArray": [],
            "map": {
                "12": "yes",
                "10": "ahum"
            },
            "emptyMap": {},
            "mixed": [2, {
                "value": "subInMulti!"
            }]
        }"""
        var index = 0

        val reader = JsonReader { input[index++] }
        listOf(
            JsonToken.StartObject to "",
            JsonToken.FieldName to "string",
            JsonToken.ObjectValue to "hey",
            JsonToken.FieldName to "int",
            JsonToken.ObjectValue to "4",
            JsonToken.FieldName to "array",
            JsonToken.StartArray to "",
            JsonToken.ArrayValue to "34",
            JsonToken.ArrayValue to "2352",
            JsonToken.ArrayValue to "3423",
            JsonToken.ArrayValue to "true",
            JsonToken.ArrayValue to "false",
            JsonToken.ArrayValue to null,
            JsonToken.EndArray to "",
            JsonToken.FieldName to "emptyArray",
            JsonToken.StartArray to "",
            JsonToken.EndArray to "",
            JsonToken.FieldName to "map",
            JsonToken.StartObject to "",
            JsonToken.FieldName to "12",
            JsonToken.ObjectValue to "yes",
            JsonToken.FieldName to "10",
            JsonToken.ObjectValue to "ahum",
            JsonToken.EndObject to "",
            JsonToken.FieldName to "emptyMap",
            JsonToken.StartObject to "",
            JsonToken.EndObject to "",
            JsonToken.FieldName to "mixed",
            JsonToken.StartArray to "",
            JsonToken.ArrayValue to "2",
            JsonToken.StartObject to "",
            JsonToken.FieldName to "value",
            JsonToken.ObjectValue to "subInMulti!",
            JsonToken.EndObject to "",
            JsonToken.EndArray to "",
            JsonToken.EndObject to ""
        ). forEach { (token, value) ->
            reader.nextToken()

            reader.currentToken shouldBe token
            reader.lastValue shouldBe value
        }

        reader.nextToken() shouldBe JsonToken.EndJSON
    }

    @Test
    fun testSkipFieldsStructure() {
        val input = """{
            "1" : 567,
            "2" : [true, false, true],
            "3" : {
                "test1": 1,
                "test2": 2,
                "array": []
            },
            "4" : true
        }"""
        var index = 0

        val reader = JsonReader { input[index++] }
        (reader.nextToken() == JsonToken.StartObject) shouldBe true

        (reader.nextToken() == JsonToken.FieldName) shouldBe true
        reader.lastValue shouldBe "1"
        reader.skipUntilNextField()

        (reader.currentToken == JsonToken.FieldName) shouldBe true
        reader.lastValue shouldBe "2"
        reader.skipUntilNextField()

        (reader.currentToken == JsonToken.FieldName) shouldBe true
        reader.lastValue shouldBe "3"
        reader.skipUntilNextField()

        (reader.currentToken == JsonToken.FieldName) shouldBe true
        reader.lastValue shouldBe "4"

        (reader.nextToken() == JsonToken.ObjectValue) shouldBe true
        reader.lastValue shouldBe "true"

        (reader.nextToken() == JsonToken.EndObject) shouldBe true
        (reader.nextToken() == JsonToken.EndJSON) shouldBe true
    }

    @Test
    fun testJsonParserNumbers() {
        val input = """[
            4,
            4.723,
            -0.123723,
            4.723E50,
            1.453E-4,
            1.453E+53,
            13453.442e4234,
            53.442e-234,
            53.442e+234
        ]"""
        var index = 0

        val reader = JsonReader { input[index++] }
        listOf(
            JsonToken.StartArray to "",
            JsonToken.ArrayValue to "4",
            JsonToken.ArrayValue to "4.723",
            JsonToken.ArrayValue to "-0.123723",
            JsonToken.ArrayValue to "4.723E50",
            JsonToken.ArrayValue to "1.453E-4",
            JsonToken.ArrayValue to "1.453E+53",
            JsonToken.ArrayValue to "13453.442e4234",
            JsonToken.ArrayValue to "53.442e-234",
            JsonToken.ArrayValue to "53.442e+234",
            JsonToken.EndArray to ""
        ). forEach { (token, value) ->
            reader.nextToken()

            reader.currentToken shouldBe token
            reader.lastValue shouldBe value
        }

        reader.nextToken() shouldBe JsonToken.EndJSON
    }

    @Test
    fun testInvalidJsonFail() {
        fun checkFaultyJSON(input: String) {
            var index = 0

            val reader = JsonReader { input[index++] }
            shouldThrow<InvalidJsonContent> {
                do {
                    reader.nextToken()
                } while (reader.currentToken !is JsonToken.Stopped)
            }
        }

        // Invalid start
        checkFaultyJSON("test")

        // False, true, null errors
        checkFaultyJSON("[falze]")
        checkFaultyJSON("[trui]")
        checkFaultyJSON("[noll]")

        // Invalid object
        checkFaultyJSON("{test}")
        checkFaultyJSON("""{"test":5, wrong:1}""")
        checkFaultyJSON("""{"test":5{""")
        checkFaultyJSON("""{"test"[""")

        // Invalid array
        checkFaultyJSON("[22332,]")

        // Invalid Numbers
        checkFaultyJSON("[007]")
        checkFaultyJSON("[-007.652]")
        checkFaultyJSON("[5.5E]")
        checkFaultyJSON("[5-3]")
        checkFaultyJSON("[34234.]")
        checkFaultyJSON("[34234.]")
    }

    @Test
    fun testSuspended() {
        var input = "[343,22452,true"
        var index = 0

        val reader = JsonReader {
            val b = input[index].also {
                // JS platform returns a 0 control char when nothing can be read
                if (it == '\u0000') {
                    throw Throwable("0 char encountered")
                }
            }
            index++
            b
        }
        do {
            reader.nextToken()
        } while (reader.currentToken !is JsonToken.Stopped)

        (reader.currentToken is JsonToken.Suspended) shouldBe true

        input += "]"

        reader.nextToken() shouldBe JsonToken.EndArray
        reader.nextToken() shouldBe JsonToken.EndJSON
    }
}