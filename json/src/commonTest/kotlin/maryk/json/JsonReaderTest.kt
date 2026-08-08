package maryk.json

import maryk.json.JsonToken.EndObject
import maryk.json.JsonToken.FieldName
import maryk.json.JsonToken.Stopped
import maryk.json.JsonToken.Suspended
import maryk.json.ValueType.Bool
import maryk.json.ValueType.Float
import maryk.json.ValueType.Int
import maryk.json.ValueType.Null
import kotlin.test.Test
import kotlin.test.assertEquals
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
        createJsonReader(input).apply {
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
        createJsonReader(input).apply {
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
        createJsonReader(input).apply {
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
        val input = """{
        |"test" "
        |}""".trimMargin()

        val reader = createJsonReader(input)
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
            val reader = createJsonReader(input)
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
        checkFaultyJSON("""{"test":5} extra""")

        // Invalid array
        checkFaultyJSON("[22332,]")
        checkFaultyJSON("""["test"] extra""")
        checkFaultyJSON(""""test" extra""")

        // Invalid strings
        checkFaultyJSON(
            """
            ["line
            break"]
            """.trimIndent()
        )
        checkFaultyJSON("[\"tab\tchar\"]")
        checkFaultyJSON("""["bad\xescape"]""")
        checkFaultyJSON("""["bad\uwrong"]""")
        checkFaultyJSON("""["bad\u0w00"]""")
        checkFaultyJSON("""["bad\uD83D"]""")
        checkFaultyJSON("""["bad\uDE0D"]""")
        checkFaultyJSON("""["bad\uD83Dx"]""")

        // Invalid Numbers
        checkFaultyJSON("[007]")
        checkFaultyJSON("[-007.652]")
        checkFaultyJSON("[-.5]")
        checkFaultyJSON("[-]")
        checkFaultyJSON("[5.5E]")
        checkFaultyJSON("[5-3]")
        checkFaultyJSON("[34234.]")
        checkFaultyJSON("[1e9999]")
    }

    @Test
    fun testSuspended() {
        var input = "[343,22452,true"
        var index = 0

        JsonReader {
            input.getOrNull(index)?.also { index++ }
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

    @Test
    fun resumesNumberSplitBeforeArrayEndWithoutDuplicateNesting() {
        var input = "[1"
        var index = 0

        JsonReader {
            input.getOrNull(index)?.also { index++ }
        }.apply {
            assertStartArray()
            assertIs<Suspended>(nextToken())

            input += "]"

            assertValue(1L, ValueType.Int)
            assertEndArray()
            assertEndDocument()
        }
    }

    @Test
    fun resumesKeywordSplitBeforeArrayEndWithoutDuplicateNesting() {
        var input = "[tru"
        var index = 0

        JsonReader {
            input.getOrNull(index)?.also { index++ }
        }.apply {
            assertStartArray()
            assertIs<Suspended>(nextToken())

            input += "e]"

            assertValue(true, ValueType.Bool)
            assertEndArray()
            assertEndDocument()
        }
    }

    @Test
    fun resumesObjectValueSplitBeforeObjectEndWithoutDuplicateNesting() {
        var input = "{\"value\":1"
        var index = 0

        JsonReader {
            input.getOrNull(index)?.also { index++ }
        }.apply {
            assertStartObject()
            assertFieldName("value")
            assertIs<Suspended>(nextToken())

            input += "}"

            assertValue(1L, ValueType.Int)
            assertEndObject()
            assertEndDocument()
        }
    }

    @Test
    fun resumesFractionSplitBeforeArrayEnd() {
        var input = "[1."
        var index = 0

        JsonReader {
            input.getOrNull(index)?.also { index++ }
        }.apply {
            assertStartArray()
            assertIs<Suspended>(nextToken())

            input += "25]"

            assertValue(1.25, ValueType.Float)
            assertEndArray()
            assertEndDocument()
        }
    }

    @Test
    fun resumesStringEscapeSplitBeforeArrayEnd() {
        var input = """["hello""" + "\\"
        var index = 0

        JsonReader {
            input.getOrNull(index)?.also { index++ }
        }.apply {
            assertStartArray()
            assertIs<Suspended>(nextToken())

            input += "n\"]"

            assertValue("hello\n")
            assertEndArray()
            assertEndDocument()
        }
    }

    @Test
    fun resumesNullKeywordSplitBeforeArrayEnd() {
        var input = "[nul"
        var index = 0

        JsonReader {
            input.getOrNull(index)?.also { index++ }
        }.apply {
            assertStartArray()
            assertIs<Suspended>(nextToken())

            input += "l]"

            assertValue(null, ValueType.Null)
            assertEndArray()
            assertEndDocument()
        }
    }

    @Test
    fun resumesCompletedFieldNameBeforeValueChunk() {
        var input = "{\"value\":"
        var index = 0

        JsonReader {
            input.getOrNull(index)?.also { index++ }
        }.apply {
            assertStartObject()
            assertIs<Suspended>(nextToken())

            input += "1}"

            assertValue(1L, ValueType.Int)
            assertEndObject()
            assertEndDocument()
        }
    }

    @Test
    fun resumesCompletedStringBeforeArrayEnd() {
        var input = "[\"value\""
        var index = 0

        JsonReader {
            input.getOrNull(index)?.also { index++ }
        }.apply {
            assertStartArray()
            assertIs<Suspended>(nextToken())

            input += "]"

            assertEndArray()
            assertEndDocument()
        }
    }

    @Test
    fun resumesAtEveryChunkBoundary() {
        val json = """{
            "number" : -12.5e+2,
            "flags" : [ true, false, null ],
            "text" : "a\n\uD83D\uDE0D",
            "nested" : { "empty" : [ ] }
        }""".trimIndent()
        val expected = collectTokenKeys(JsonReader(json))

        for (split in 0..json.length) {
            var input = json.substring(0, split)
            var index = 0
            val actualReader = JsonReader {
                input.getOrNull(index)?.also { index++ }
            }
            val actual = mutableListOf<String>()

            collectUntilStopped(actualReader, actual)
            if (split < json.length) {
                input += json.substring(split)
                collectUntilStopped(actualReader, actual)
            }

            assertEquals(expected, actual, "split=$split")
        }
    }

    @Test
    fun resumesAcrossEveryCharacterChunk() {
        val json = """{ "number" : -12.5e+2, "flags" : [ true, false, null ], "text" : "a\\n\\uD83D\\uDE0D" }"""
        val expected = collectTokenKeys(JsonReader(json))
        var input = ""
        var index = 0
        val reader = JsonReader {
            input.getOrNull(index)?.also { index++ }
        }
        val actual = mutableListOf<String>()

        json.forEachIndexed { chunkIndex, char ->
            input += char
            collectUntilStopped(reader, actual)
            assertTrue(actual.none { it == "suspended" }, "chunk=$chunkIndex, input=$input")
        }

        assertEquals(expected, actual)
    }

    @Test
    fun resumesStringAcrossMultipleChunks() {
        var input = "[\"a"
        var index = 0

        JsonReader {
            input.getOrNull(index)?.also { index++ }
        }.apply {
            assertStartArray()
            assertIs<Suspended>(nextToken())

            input += "b"
            assertIs<Suspended>(nextToken())

            input += " c"
            assertIs<Suspended>(nextToken())

            input += "d\"]"
            assertValue("ab cd")
            assertEndArray()
            assertEndDocument()
        }
    }

    @Test
    fun exposesPartialStringInSuspension() {
        var input = "[\"hel"
        var index = 0

        JsonReader {
            input.getOrNull(index)?.also { index++ }
        }.apply {
            assertStartArray()
            assertEquals("hel", assertIs<Suspended>(nextToken()).storedValue)

            input += "lo\"]"
            assertValue("hello")
            assertEndArray()
            assertEndDocument()
        }
    }

    @Test
    fun exposesPartialNumberInSuspension() {
        var input = "[12"
        var index = 0

        JsonReader {
            input.getOrNull(index)?.also { index++ }
        }.apply {
            assertStartArray()
            assertEquals("12", assertIs<Suspended>(nextToken()).storedValue)

            input += "]"
            assertValue(12L, ValueType.Int)
            assertEndArray()
            assertEndDocument()
        }
    }

    @Test
    fun reportsLocationForInvalidStringCharacterAfterResume() {
        var input = "[\n\"ab"
        var index = 0
        val reader = JsonReader {
            input.getOrNull(index)?.also { index++ }
        }

        reader.assertStartArray()
        assertIs<Suspended>(reader.nextToken())

        input += "\n"

        val error = assertFailsWith<InvalidJsonContent> {
            reader.nextToken()
        }
        assertEquals(3, error.lineNumber)
        assertEquals(0, error.columnNumber)
        assertEquals(3, reader.lineNumber)
        assertEquals(0, reader.columnNumber)
    }

    @Test
    fun resumesCompletedFieldNameBeforeColonAcrossChunks() {
        var input = "{\"hel"
        var index = 0

        JsonReader {
            input.getOrNull(index)?.also { index++ }
        }.apply {
            assertStartObject()
            assertIs<Suspended>(nextToken())

            input += "lo\""
            assertIs<Suspended>(nextToken())

            input += ":1}"
            assertFieldName("hello")
            assertValue(1L, ValueType.Int)
            assertEndObject()
            assertEndDocument()
        }
    }

    @Test
    fun resumesFieldNameAfterWhitespaceBeforeColon() {
        var input = "{\"hel"
        var index = 0

        JsonReader {
            input.getOrNull(index)?.also { index++ }
        }.apply {
            assertStartObject()
            assertIs<Suspended>(nextToken())

            input += "lo\" "
            assertIs<Suspended>(nextToken())

            input += ":1}"
            assertFieldName("hello")
            assertValue(1L, ValueType.Int)
            assertEndObject()
            assertEndDocument()
        }
    }

    @Test
    fun resumesNumberAcrossMultipleChunks() {
        var input = "[12"
        var index = 0

        JsonReader {
            input.getOrNull(index)?.also { index++ }
        }.apply {
            assertStartArray()
            assertIs<Suspended>(nextToken())

            input += "34"
            assertIs<Suspended>(nextToken())

            input += "56"
            assertIs<Suspended>(nextToken())

            input += "]"
            assertValue(123456L, ValueType.Int)
            assertEndArray()
            assertEndDocument()
        }
    }

    @Test
    fun preservesCompletedNumberInSuspensionBeforeArrayEnd() {
        var input = "[1 "
        var index = 0

        JsonReader {
            input.getOrNull(index)?.also { index++ }
        }.apply {
            assertStartArray()
            val suspended = assertIs<Suspended>(nextToken())
            assertEquals(1L, assertIs<JsonToken.Value<*>>(suspended.lastToken).value)

            input += "]"
            assertEndArray()
            assertEndDocument()
        }
    }

    @Test
    fun resumesWhitespaceBeforeArrayValueAcrossMultipleChunks() {
        var input = "["
        var index = 0

        JsonReader {
            input.getOrNull(index)?.also { index++ }
        }.apply {
            assertEquals(JsonToken.StartDocument, assertIs<Suspended>(nextToken()).lastToken)

            input += " "
            assertEquals(JsonToken.StartDocument, assertIs<Suspended>(nextToken()).lastToken)

            input += "\n"
            assertEquals(JsonToken.StartDocument, assertIs<Suspended>(nextToken()).lastToken)

            input += "]"
            assertStartArray()
            assertEndArray()
            assertEndDocument()
        }
    }

    private fun collectTokenKeys(reader: JsonReader): List<String> {
        val tokens = mutableListOf<String>()
        collectUntilStopped(reader, tokens)
        return tokens
    }

    private fun collectUntilStopped(reader: JsonReader, tokens: MutableList<String>) {
        while (true) {
            when (val token = reader.nextToken()) {
                is Suspended -> {
                    val committedToken = token.lastToken
                    if (
                        committedToken !in setOf(
                            JsonToken.StartDocument,
                            JsonToken.ObjectSeparator,
                            JsonToken.ArraySeparator,
                        ) && tokens.lastOrNull() != tokenKey(committedToken)
                    ) {
                        tokens += tokenKey(committedToken)
                    }
                    return
                }
                is JsonToken.EndDocument -> {
                    tokens += tokenKey(token)
                    return
                }
                else -> tokens += tokenKey(token)
            }
        }
    }

    private fun tokenKey(token: JsonToken) = when (token) {
        JsonToken.StartDocument -> "start"
        is JsonToken.StartObject -> "start-object"
        JsonToken.EndObject -> "end-object"
        is JsonToken.FieldName -> "field:${token.value}"
        JsonToken.ObjectSeparator -> "object-separator"
        is JsonToken.Value<*> -> "value:${token.type}:${token.value}"
        is JsonToken.StartArray -> "start-array"
        JsonToken.ArraySeparator -> "array-separator"
        JsonToken.EndArray -> "end-array"
        JsonToken.EndDocument -> "end-document"
        is JsonToken.Suspended -> "suspended"
        is JsonToken.JsonException -> "exception:${token.e}"
        JsonToken.StartComplexFieldName -> "start-complex-field-name"
        JsonToken.EndComplexFieldName -> "end-complex-field-name"
        else -> token.name
    }

    @Test
    fun testSuspendedStringValue() {
        var input = """["hel"""
        var index = 0

        JsonReader {
            input.getOrNull(index)?.also { index++ }
        }.apply {
            do {
                nextToken()
            } while (currentToken !is Stopped)

            assertTrue { currentToken is Suspended }

            input += """lo"]"""

            assertValue("hello")
            assertEndArray()
            assertEndDocument()
        }
    }

    @Test
    fun testSuspendedStringUtfEscape() {
        var input = """["\uD83"""
        var index = 0

        JsonReader {
            input.getOrNull(index)?.also { index++ }
        }.apply {
            do {
                nextToken()
            } while (currentToken !is Stopped)

            assertTrue { currentToken is Suspended }

            input += """D\uDE0D"]"""

            assertValue("😍")
            assertEndArray()
            assertEndDocument()
        }
    }

    @Test
    fun testSuspendedStringFieldName() {
        var input = """{"hel"""
        var index = 0

        JsonReader {
            input.getOrNull(index)?.also { index++ }
        }.apply {
            do {
                nextToken()
            } while (currentToken !is Stopped)

            assertTrue { currentToken is Suspended }

            input += """lo": 1}"""

            assertFieldName("hello")
            assertValue(1L, ValueType.Int)
            assertEndObject()
            assertEndDocument()
        }
    }

    private fun createJsonReader(input: String): JsonReader {
        var index = 0

        return JsonReader {
            input.getOrNull(index++)
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
    fun readsRootScalarsAtDefinitiveStringEndOfInput() {
        assertRootValue("42", 42L, Int)
        assertRootValue("-42", -42L, Int)
        assertRootValue("1.25e-2", 0.0125, Float)
        assertRootValue("true", true, Bool)
        assertRootValue("false", false, Bool)
        assertRootValue("null", null, Null)
    }

    @Test
    fun readsRootScalarsWithTrailingWhitespaceAtDefinitiveStringEndOfInput() {
        assertRootValue("42 \n\t", 42L, Int)
        assertRootValue("-42 \n\t", -42L, Int)
        assertRootValue("1.25e-2 \n\t", 0.0125, Float)
        assertRootValue("true \n\t", true, Bool)
        assertRootValue("false \n\t", false, Bool)
        assertRootValue("null \n\t", null, Null)
    }

    @Test
    fun rejectsWhitespaceOutsideTheJsonSpecification() {
        listOf("\u000B42", "42\u000C", "[\u00A01]").forEach { input ->
            assertFailsWith<InvalidJsonContent>(input) {
                JsonReader(input).apply {
                    do {
                        nextToken()
                    } while (currentToken !is Stopped)
                }
            }
        }
    }

    @Test
    fun readsScalarsInsideContainers() {
        JsonReader("[42, -42, 1.25e-2, true, false, null]").apply {
            assertStartArray()
            assertValue(42L, Int)
            assertValue(-42L, Int)
            assertValue(0.0125, Float)
            assertValue(true, Bool)
            assertValue(false, Bool)
            assertValue(null, Null)
            assertEndArray()
            assertEndDocument()
        }
    }

    @Test
    fun readsRootScalarsAtLambdaEndOfInput() {
        assertLambdaRootValue("42", 42L, Int)
        assertLambdaRootValue("true", true, Bool)
        assertLambdaRootValue("false", false, Bool)
        assertLambdaRootValue("null", null, Null)
    }

    @Test
    fun suspendsIncompleteKeywordInsideDefinitiveContainer() {
        JsonReader("[tru").apply {
            assertStartArray()

            assertIs<Suspended>(nextToken())
        }
    }

    @Test
    fun rejectsIncompleteRootScalarsFromDefinitiveStringInput() {
        listOf("-", "1.", "1e", "tru", "nul", "42x").forEach { input ->
            assertFailsWith<InvalidJsonContent> {
                JsonReader(input).nextToken()
            }
        }
    }

    @Test
    fun readDoubleQuoteWithSpecialChars() {
        createJsonReader("""["te\"\b\f\n\t\\\/\r'"]""").apply {
            assertStartArray()
            assertValue("te\"\b\u000C\n\t\\/\r'")
            assertEndArray()
            assertEndDocument()
        }
    }

    @Test
    fun readDoubleQuoteWithUtfChars() {
        val utfEscapes = buildString {
            append("\\")
            append("uD83D")
            append("\\")
            append("uDE0D")
        }
        createJsonReader("""["$utfEscapes"]""").apply {
            assertStartArray()
            assertValue("😍")
            assertEndArray()
            assertEndDocument()
        }
    }

    private fun assertRootValue(input: String, value: Any?, type: ValueType<*>) {
        JsonReader(input).apply {
            assertIs<JsonToken.Value<*>>(nextToken()).apply {
                expect(value) { this.value }
                expect(type) { this.type }
            }
            assertEndDocument()
        }
    }

    private fun assertLambdaRootValue(input: String, value: Any?, type: ValueType<*>) {
        var index = 0
        JsonReader { input.getOrNull(index++) }.apply {
            assertIs<JsonToken.Value<*>>(nextToken()).apply {
                expect(value) { this.value }
                expect(type) { this.type }
            }
            assertEndDocument()
        }
    }
}
