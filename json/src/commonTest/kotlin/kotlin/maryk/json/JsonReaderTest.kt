package maryk.json

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

        JsonReader { input[index++] }.apply {
            assertStartObject()
            assertFieldName("string")
            assertValue("hey", ValueType.String)
            assertFieldName("int")
            assertValue(4L, ValueType.Int)
            assertFieldName("array")
            assertStartArray()
            assertValue(34L, ValueType.Int)
            assertValue(2352L, ValueType.Int)
            assertValue(3423L, ValueType.Int)
            assertValue(true, ValueType.Bool)
            assertValue(false, ValueType.Bool)
            assertValue(null, ValueType.Null)
            assertEndArray()
            assertFieldName("emptyArray")
            assertStartArray()
            assertEndArray()
            assertFieldName("map")
            assertStartObject()
            assertFieldName("12")
            assertValue("yes", ValueType.String)
            assertFieldName("10")
            assertValue("ahum", ValueType.String)
            assertEndObject()
            assertFieldName("emptyMap")
            assertStartObject()
            assertEndObject()
            assertFieldName("mixed")
            assertStartArray()
            assertValue(2, ValueType.Int)
            assertStartObject()
            assertFieldName("value")
            assertValue("subInMulti!", ValueType.String)
            assertEndObject()
            assertEndArray()
            assertEndObject()
            assertEndDocument()
        }
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

        JsonReader { input[index++] }.apply {
            assertStartObject()

            assertFieldName("1")
            skipUntilNextField()

            currentToken.apply {
                (this is JsonToken.FieldName) shouldBe true
                (this as JsonToken.FieldName).value shouldBe "2"
            }
            skipUntilNextField()

            currentToken.apply {
                (this is JsonToken.FieldName) shouldBe true
                (this as JsonToken.FieldName).value shouldBe "3"
            }
            skipUntilNextField()

            currentToken.apply {
                (this is JsonToken.FieldName) shouldBe true
                (this as JsonToken.FieldName).value shouldBe "4"
            }

            assertValue(true, ValueType.Bool)

            assertEndObject()
            assertEndDocument()
        }

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
            13453.442e234,
            53.442e-234,
            53.442e+234
        ]"""
        var index = 0

        JsonReader { input[index++] }.apply {
            assertStartArray()
            assertValue(4L, ValueType.Int)
            assertValue(4.723, ValueType.Float)
            assertValue(-0.123723, ValueType.Float)
            assertValue(4.723E50, ValueType.Float)
            assertValue(1.453E-4, ValueType.Float)
            assertValue(1.453E+53, ValueType.Float)
            assertValue(13453.442e234, ValueType.Float)
            assertValue(53.442e-234, ValueType.Float)
            assertValue(53.442e+234, ValueType.Float)
            assertEndArray()
            assertEndDocument()
        }
    }

    @Test
    fun testInvalidJsonFailMessage() {
        var index = 0
        val input = """{
        |"test" "
        |}""".trimMargin()

        val reader = JsonReader { input[index++] }
        val e = shouldThrow<InvalidJsonContent> {
            do {
                reader.nextToken()
            } while (reader.currentToken !is JsonToken.Stopped)
        }

        e.lineNumber shouldBe 2
        e.columnNumber shouldBe 8

        e.message shouldBe """[l: 2, c: 8] Invalid character '"' after FieldName(test)"""

        reader.lineNumber shouldBe 2
        reader.columnNumber shouldBe 8
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
    }

    @Test
    fun testSuspended() {
        var input = "[343,22452,true"
        var index = 0

        JsonReader {
            val b = input[index].also {
                // JS platform returns a 0 control char when nothing can be read
                if (it == '\u0000') {
                    throw Throwable("0 char encountered")
                }
            }
            index++
            b
        }.apply {
            do {
                nextToken()
            } while (currentToken !is JsonToken.Stopped)

            (currentToken is JsonToken.Suspended) shouldBe true

            input += "]"

            assertEndArray()
            assertEndDocument()
        }
    }

    private fun createJsonReader(input: String): JsonReader {
        var index = 0

        return JsonReader {
            val b = input[index].also {
                // JS platform returns a 0 control char when nothing can be read
                if (it == '\u0000') {
                    throw Throwable("0 char encountered")
                }
            }
            index++
            b
        }
    }

    @Test
    fun readDoubleQuote() {
        createJsonReader("""["test"]""").apply {
            assertStartArray()
            assertValue("test")
            assertEndArray()
            assertEndDocument()
        }
    }

    @Test
    fun readDoubleQuoteWithSpecialChars() {
        createJsonReader("""["te\"\b\f\n\t\\\/\r'\x"]""").apply {
            assertStartArray()
            assertValue("te\"\b\u000C\n\t\\/\r'\\x")
            assertEndArray()
            assertEndDocument()
        }
    }

    @Test
    fun readDoubleQuoteWithUtfChars() {
        createJsonReader("""["\uD83D\uDE0D\uwrong\u0w\u00w\u000w"]""").apply {
            assertStartArray()
            assertValue("😍\\uwrong\\u0w\\u00w\\u000w")
            assertEndArray()
            assertEndDocument()
        }
    }
}
