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
            JsonToken.SimpleStartObject,
            JsonToken.FieldName("string"),
            JsonToken.Value("hey", ValueType.String),
            JsonToken.FieldName("int"),
            JsonToken.Value("4", ValueType.String),
            JsonToken.FieldName("array"),
            JsonToken.SimpleStartArray,
            JsonToken.Value("34", ValueType.String),
            JsonToken.Value("2352", ValueType.String),
            JsonToken.Value("3423", ValueType.String),
            JsonToken.Value("true", ValueType.String),
            JsonToken.Value("false", ValueType.String),
            JsonToken.Value(null, ValueType.Null),
            JsonToken.EndArray,
            JsonToken.FieldName("emptyArray"),
            JsonToken.SimpleStartArray,
            JsonToken.EndArray,
            JsonToken.FieldName("map"),
            JsonToken.SimpleStartObject,
            JsonToken.FieldName("12"),
            JsonToken.Value("yes", ValueType.String),
            JsonToken.FieldName("10"),
            JsonToken.Value("ahum", ValueType.String),
            JsonToken.EndObject,
            JsonToken.FieldName("emptyMap"),
            JsonToken.SimpleStartObject,
            JsonToken.EndObject,
            JsonToken.FieldName("mixed"),
            JsonToken.SimpleStartArray,
            JsonToken.Value("2", ValueType.String),
            JsonToken.SimpleStartObject,
            JsonToken.FieldName("value"),
            JsonToken.Value("subInMulti!", ValueType.String),
            JsonToken.EndObject,
            JsonToken.EndArray,
            JsonToken.EndObject
        ). forEach { token ->
            reader.nextToken().apply {
                this.name shouldBe token.name
                if (this is JsonToken.Value<*>) {
                    this.value shouldBe (token as JsonToken.Value<*>).value
                    this.type shouldBe token.type
                }
            }
        }

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
        (reader.nextToken() == JsonToken.SimpleStartObject) shouldBe true

        reader.nextToken().apply {
            (this is JsonToken.FieldName) shouldBe true
            (this as JsonToken.FieldName).value shouldBe "1"
        }
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

        reader.nextToken().apply {
            (this is JsonToken.Value<*>) shouldBe true
            (this as JsonToken.Value<*>).value shouldBe "true"
        }

        (reader.nextToken() == JsonToken.EndObject) shouldBe true
        (reader.nextToken() == JsonToken.EndDocument) shouldBe true
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
            JsonToken.SimpleStartArray,
            JsonToken.Value("4", ValueType.String),
            JsonToken.Value("4.723", ValueType.String),
            JsonToken.Value("-0.123723", ValueType.String),
            JsonToken.Value("4.723E50", ValueType.String),
            JsonToken.Value("1.453E-4", ValueType.String),
            JsonToken.Value("1.453E+53", ValueType.String),
            JsonToken.Value("13453.442e4234", ValueType.String),
            JsonToken.Value("53.442e-234", ValueType.String),
            JsonToken.Value("53.442e+234", ValueType.String),
            JsonToken.EndArray
        ). forEach { token ->
            reader.nextToken().apply {
                this.name shouldBe token.name
                if (this is JsonToken.Value<*>) {
                    this.value shouldBe (token as JsonToken.Value<*>).value
                }
            }
        }

        reader.nextToken() shouldBe JsonToken.EndDocument
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
        reader.nextToken() shouldBe JsonToken.EndDocument
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