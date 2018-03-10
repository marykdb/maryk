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

        testForObjectStart(reader)
        testForFieldName(reader, "string")
        testForValue(reader, "hey", ValueType.String)
        testForFieldName(reader, "int")
        testForValue(reader, 4, ValueType.Int)
        testForFieldName(reader, "array")
        testForArrayStart(reader)
        testForValue(reader, 34, ValueType.Int)
        testForValue(reader, 2352, ValueType.Int)
        testForValue(reader, 3423, ValueType.Int)
        testForValue(reader, true, ValueType.Bool)
        testForValue(reader, false, ValueType.Bool)
        testForValue(reader, null, ValueType.Null)
        testForArrayEnd(reader)
        testForFieldName(reader, "emptyArray")
        testForArrayStart(reader)
        testForArrayEnd(reader)
        testForFieldName(reader, "map")
        testForObjectStart(reader)
        testForFieldName(reader, "12")
        testForValue(reader, "yes", ValueType.String)
        testForFieldName(reader, "10")
        testForValue(reader, "ahum", ValueType.String)
        testForObjectEnd(reader)
        testForFieldName(reader, "emptyMap")
        testForObjectStart(reader)
        testForObjectEnd(reader)
        testForFieldName(reader, "mixed")
        testForArrayStart(reader)
        testForValue(reader, 2, ValueType.Int)
        testForObjectStart(reader)
        testForFieldName(reader, "value")
        testForValue(reader, "subInMulti!", ValueType.String)
        testForObjectEnd(reader)
        testForArrayEnd(reader)
        testForObjectEnd(reader)

        reader.nextToken() shouldBe JsonToken.EndDocument
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
        testForObjectStart(reader)

        testForFieldName(reader, "1")
        reader.skipUntilNextField()

        reader.currentToken.apply {
            (this is JsonToken.FieldName) shouldBe true
            (this as JsonToken.FieldName).value shouldBe "2"
        }
        reader.skipUntilNextField()

        reader.currentToken.apply {
            (this is JsonToken.FieldName) shouldBe true
            (this as JsonToken.FieldName).value shouldBe "3"
        }
        reader.skipUntilNextField()

        reader.currentToken.apply {
            (this is JsonToken.FieldName) shouldBe true
            (this as JsonToken.FieldName).value shouldBe "4"
        }

        testForValue(reader, true, ValueType.Bool)

        testForObjectEnd(reader)
        testForDocumentEnd(reader)
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

        val reader = JsonReader { input[index++] }

        testForArrayStart(reader)
        testForValue(reader, 4, ValueType.Int)
        testForValue(reader, 4.723, ValueType.Float)
        testForValue(reader, -0.123723, ValueType.Float)
        testForValue(reader, 4.723E50, ValueType.Float)
        testForValue(reader, 1.453E-4, ValueType.Float)
        testForValue(reader, 1.453E+53, ValueType.Float)
        testForValue(reader, 13453.442e234, ValueType.Float)
        testForValue(reader, 53.442e-234, ValueType.Float)
        testForValue(reader, 53.442e+234, ValueType.Float)
        testForArrayEnd(reader)

        testForDocumentEnd(reader)
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

        testForArrayEnd(reader)
        testForDocumentEnd(reader)
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
    fun read_double_quote() {
        val reader = createJsonReader("""["test"]""")
        testForArrayStart(reader)
        testForValue(reader, "test")
        testForArrayEnd(reader)
        testForDocumentEnd(reader)
    }

    @Test
    fun read_double_quote_with_special_chars() {
        val reader = createJsonReader("""["te\"\b\f\n\t\\\/\r'"]""")
        testForArrayStart(reader)
        testForValue(reader, "te\"\b\u000C\n\t\\/\r'")
        testForArrayEnd(reader)
        testForDocumentEnd(reader)
    }

    @Test
    fun read_double_quote_with_utf_chars() {
        val reader = createJsonReader("""["\uD83D\uDE0D\uwrong\u0w\u00w\u000w"]""")
        testForArrayStart(reader)
        testForValue(reader, "😍\\uwrong\\u0w\\u00w\\u000w")
        testForArrayEnd(reader)
        testForDocumentEnd(reader)
    }
}