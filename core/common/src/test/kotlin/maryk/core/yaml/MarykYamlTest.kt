package maryk.core.yaml

import maryk.core.properties.definitions.PropertyDefinitionType
import maryk.json.IsJsonLikeReader
import maryk.json.JsonToken
import maryk.test.shouldBe
import maryk.test.shouldBeOfType
import kotlin.test.Test

fun createMarykYamlReader(yaml: String): IsJsonLikeReader {
    val input = yaml
    var index = 0

    var alreadyRead = ""

    return MarykYamlReader {
        val b = input[index].also {
            // JS platform returns a 0 control char when nothing can be read
            if (it == '\u0000') {
                throw Throwable("0 char encountered")
            }
        }
        alreadyRead += b
        index++
        b
    }
}



class MarykYamlTest{
    @Test
    fun read_maryk_tags() {
        createMarykYamlReader("""
        |    - !Boolean { k1: v1 }
        |    - !String { k2: v2 }
        """.trimMargin()).apply {
            shouldBeOfType<JsonToken.StartArray>(nextToken())

            shouldBeOfType<JsonToken.StartObject>(
                nextToken()
            ).type shouldBe PropertyDefinitionType.Boolean

            shouldBeOfType<JsonToken.FieldName>(
                nextToken()
            ).value shouldBe "k1"

            shouldBeOfType<JsonToken.Value<*>>(
                nextToken()
            ).value shouldBe "v1"

            shouldBeOfType<JsonToken.EndObject>(nextToken())

            shouldBeOfType<JsonToken.StartObject>(
                nextToken()
            ).type shouldBe PropertyDefinitionType.String

            shouldBeOfType<JsonToken.FieldName>(
                nextToken()
            ).value shouldBe "k2"

            shouldBeOfType<JsonToken.Value<*>>(
                nextToken()
            ).value shouldBe "v2"

            shouldBeOfType<JsonToken.EndObject>(
                nextToken()
            )

            shouldBeOfType<JsonToken.EndArray>(nextToken())
            shouldBeOfType<JsonToken.EndDocument>(nextToken())
        }
    }
}
