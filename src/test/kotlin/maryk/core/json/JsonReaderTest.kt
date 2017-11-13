package maryk.core.json

import io.kotlintest.matchers.shouldBe
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

        val reader = JsonReader() { input[index++] }
        listOf<Pair<JsonToken, String>>(
                JsonToken.START_OBJECT to "",
                JsonToken.FIELD_NAME to "string",
                JsonToken.OBJECT_VALUE to "hey",
                JsonToken.FIELD_NAME to "int",
                JsonToken.OBJECT_VALUE to "4",
                JsonToken.FIELD_NAME to "array",
                JsonToken.START_ARRAY to "",
                JsonToken.ARRAY_VALUE to "34",
                JsonToken.ARRAY_VALUE to "2352",
                JsonToken.ARRAY_VALUE to "3423",
                JsonToken.ARRAY_VALUE to "true",
                JsonToken.ARRAY_VALUE to "false",
                JsonToken.ARRAY_VALUE to "null",
                JsonToken.END_ARRAY to "",
                JsonToken.FIELD_NAME to "emptyArray",
                JsonToken.START_ARRAY to "",
                JsonToken.END_ARRAY to "",
                JsonToken.FIELD_NAME to "map",
                JsonToken.START_OBJECT to "",
                JsonToken.FIELD_NAME to "12",
                JsonToken.OBJECT_VALUE to "yes",
                JsonToken.FIELD_NAME to "10",
                JsonToken.OBJECT_VALUE to "ahum",
                JsonToken.END_OBJECT to "",
                JsonToken.FIELD_NAME to "emptyMap",
                JsonToken.START_OBJECT to "",
                JsonToken.END_OBJECT to "",
                JsonToken.FIELD_NAME to "mixed",
                JsonToken.START_ARRAY to "",
                JsonToken.ARRAY_VALUE to "2",
                JsonToken.START_OBJECT to "",
                JsonToken.FIELD_NAME to "value",
                JsonToken.OBJECT_VALUE to "subInMulti!",
                JsonToken.END_OBJECT to "",
                JsonToken.END_ARRAY to "",
                JsonToken.END_OBJECT to ""
        ). forEach { (token, value) ->
            reader.nextToken()

            reader.currentToken shouldBe token
            reader.lastValue shouldBe value
        }

        reader.nextToken() shouldBe JsonToken.END_JSON
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

        val reader = JsonReader() { input[index++] }
        (reader.nextToken() is JsonToken.START_OBJECT) shouldBe true

        (reader.nextToken() is JsonToken.FIELD_NAME) shouldBe true
        reader.lastValue shouldBe "1"
        reader.skipUntilNextField()

        (reader.currentToken is JsonToken.FIELD_NAME) shouldBe true
        reader.lastValue shouldBe "2"
        reader.skipUntilNextField()

        (reader.currentToken is JsonToken.FIELD_NAME) shouldBe true
        reader.lastValue shouldBe "3"
        reader.skipUntilNextField()

        (reader.currentToken is JsonToken.FIELD_NAME) shouldBe true
        reader.lastValue shouldBe "4"

        (reader.nextToken() is JsonToken.OBJECT_VALUE) shouldBe true
        reader.lastValue shouldBe "true"

        (reader.nextToken() is JsonToken.END_OBJECT) shouldBe true
        (reader.nextToken() is JsonToken.END_JSON) shouldBe true
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

        val reader = JsonReader() { input[index++] }
        listOf<Pair<JsonToken, String>>(
                JsonToken.START_ARRAY to "",
                JsonToken.ARRAY_VALUE to "4",
                JsonToken.ARRAY_VALUE to "4.723",
                JsonToken.ARRAY_VALUE to "-0.123723",
                JsonToken.ARRAY_VALUE to "4.723E50",
                JsonToken.ARRAY_VALUE to "1.453E-4",
                JsonToken.ARRAY_VALUE to "1.453E+53",
                JsonToken.ARRAY_VALUE to "13453.442e4234",
                JsonToken.ARRAY_VALUE to "53.442e-234",
                JsonToken.ARRAY_VALUE to "53.442e+234",
                JsonToken.END_ARRAY to ""
        ). forEach { (token, value) ->
            reader.nextToken()

            reader.currentToken shouldBe token
            reader.lastValue shouldBe value
        }

        reader.nextToken() shouldBe JsonToken.END_JSON
    }

    @Test
    fun testInvalidJsonFail() {
        fun checkFaultyJSON(input: String) {
            var index = 0

            val reader = JsonReader() { input[index++] }
            shouldThrow<InvalidJsonContent> {
                do {
                    reader.nextToken()
                } while (reader.currentToken !is JsonToken.STOPPED)
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

        val reader = JsonReader() {
            val b = input[index]
            index++
            b
        }
        do {
            reader.nextToken()
        } while (reader.currentToken !is JsonToken.STOPPED)

        (reader.currentToken is JsonToken.SUSPENDED) shouldBe true

        input += "]"

        reader.nextToken() shouldBe JsonToken.END_ARRAY
        reader.nextToken() shouldBe JsonToken.END_JSON
    }
}