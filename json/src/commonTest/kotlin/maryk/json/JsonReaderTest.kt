package maryk.json

import maryk.json.JsonToken.EndObject
import maryk.json.JsonToken.FieldName
import maryk.json.JsonToken.Stopped
import maryk.json.JsonToken.Suspended
import maryk.json.ValueType.Bool
import maryk.json.ValueType.Null
import kotlin.test.Test
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlin.test.expect

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
            assertValue(true, Bool)
            assertValue(false, Bool)
            assertValue(null, Null)
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
            "4" : true,
            "5" : {
                "test1": 1,
                "test2": 2,
                "array": []
            }
        }
        """
        var index = 0

        JsonReader { input[index++] }.apply {
            assertStartObject()

            assertFieldName("1")
            skipUntilNextField()

            expect("2") { assertIs<FieldName>(currentToken).value }
            skipUntilNextField()

            expect("3") { assertIs<FieldName>(currentToken).value }
            skipUntilNextField()

            expect("4") { assertIs<FieldName>(currentToken).value }

            assertValue(true, Bool)

            nextToken()

            expect("5") { assertIs<FieldName>(currentToken).value }
            skipUntilNextField()

            expect(EndObject) { this.currentToken }
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
        val e = assertFailsWith<InvalidJsonContent>(
            message = """[l: 2, c: 8] Invalid character '"' after FieldName(test)"""
        ) {
            do {
                reader.nextToken()
            } while (reader.currentToken !is Stopped)
        }

        expect(2) { e.lineNumber }
        expect(8) { e.columnNumber }

        expect(2) { reader.lineNumber }
        expect(8) { reader.columnNumber }
    }

    @Test
    fun testInvalidJsonFail() {
        fun checkFaultyJSON(input: String) {
            var index = 0

            val reader = JsonReader { input[index++] }
            assertFailsWith<InvalidJsonContent> {
                do {
                    reader.nextToken()
                } while (reader.currentToken !is Stopped)
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
            input.getOrNull(index)?.also { index++ } ?: throw Throwable("0 char encountered")
        }.apply {
            do {
                nextToken()
            } while (currentToken !is Stopped)

            assertTrue { currentToken is Suspended }

            input += "]"

            assertEndArray()
            assertEndDocument()
        }
    }

    private fun createJsonReader(input: String): JsonReader {
        var index = 0

        return JsonReader {
            input.getOrNull(index)?.also { index++ } ?: throw Throwable("0 char encountered")
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
    fun readSimpleValue() {
        createJsonReader(""""test"""").apply {
            assertValue("test")
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
