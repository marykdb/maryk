package maryk.json

import maryk.json.JsonToken.EndArray
import maryk.json.JsonToken.FieldName
import maryk.json.JsonToken.SimpleStartArray
import maryk.json.JsonToken.SimpleStartObject
import maryk.json.JsonToken.Stopped
import maryk.json.JsonToken.Value
import maryk.json.ValueType.String
import maryk.test.shouldBe
import kotlin.test.Test

class PresetJsonTokenReaderTest {
    @Test
    fun returnPresetJsonTokens() {
        val tokens = listOf(
            SimpleStartObject,
            FieldName("a"),
            Value("1", String),
            FieldName("b"),
            SimpleStartArray,
            Value(1, ValueType.Int),
            Value(2, ValueType.Int),
            Value(3, ValueType.Int),
            EndArray,
            FieldName("c"),
            Value("3", String)
        )

        val tokenReader = PresetJsonTokenReader(
            tokens
        )

        var index = 0

        do {
            if (tokens.lastIndex > index) {
                tokenReader.currentToken.let {
                    it shouldBe tokens[index]

                    if (it is FieldName && it.value == "b") {
                        tokenReader.skipUntilNextField()
                        index += 6
                    }
                }
            }

            tokenReader.nextToken()

            index++
        } while (tokenReader.currentToken !is Stopped)
    }
}
