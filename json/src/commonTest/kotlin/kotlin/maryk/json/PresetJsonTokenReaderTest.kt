package maryk.json

import maryk.test.shouldBe
import kotlin.test.Test

class PresetJsonTokenReaderTest {
    @Test
    fun return_preset_json_tokens() {
        val tokens = listOf(
            JsonToken.SimpleStartObject,
            JsonToken.FieldName("a"),
            JsonToken.Value("1", ValueType.String),
            JsonToken.FieldName("b"),
            JsonToken.SimpleStartArray,
            JsonToken.Value(1, ValueType.Int),
            JsonToken.Value(2, ValueType.Int),
            JsonToken.Value(3, ValueType.Int),
            JsonToken.EndArray,
            JsonToken.FieldName("c"),
            JsonToken.Value("3", ValueType.String)
        )

        val tokenReader = PresetJsonTokenReader(
            tokens
        )

        var index = 0

        do {
            if (tokens.lastIndex > index) {
                tokenReader.currentToken.let {
                    it shouldBe tokens[index]

                    if (it is JsonToken.FieldName && it.value == "b") {
                        tokenReader.skipUntilNextField()
                        index += 6
                    }
                }
            }

            tokenReader.nextToken()

            index++
        } while (tokenReader.currentToken !is JsonToken.Stopped)
    }
}
